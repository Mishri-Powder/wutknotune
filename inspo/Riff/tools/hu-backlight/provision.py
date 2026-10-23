#!/usr/bin/env python3
"""Install/update or explicitly reset a selected direct HU, then provision its partial Internal config."""

import argparse
import importlib.util
import json
import shlex
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HELPER_PACKAGE = 'link.roadio.riff.huprovision'
HELPER_CLASS = HELPER_PACKAGE + '.HuConfigurationProvisioner'


def run(args):
    result = subprocess.run([str(arg) for arg in args], check=True,
                            stdin=subprocess.DEVNULL, capture_output=True, text=True)
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Explicit operator-selected HU ADB serial')
    parser.add_argument('--package', default='link.roadio.riff',
                        choices=['link.roadio.riff', 'link.roadio.riff.instrumented'])
    parser.add_argument('--apk', type=Path, help='Optional signed in-place update before provisioning')
    parser.add_argument('--reset-data', action='store_true', help='Explicitly erase selected package data')
    parser.add_argument('--confirm-reset-working-data', action='store_true')
    parser.add_argument('--no-launch', action='store_true')
    parser.add_argument('--output', type=Path, required=True, help='New project status directory')
    args = parser.parse_args()
    if args.reset_data and args.package == 'link.roadio.riff' and not args.confirm_reset_working_data:
        parser.error('Working reset requires --confirm-reset-working-data; ordinary installs preserve data')
    args.output.mkdir(parents=True, exist_ok=False)
    spec = importlib.util.spec_from_file_location('riff_release', ROOT / 'bin/build_release.py')
    release = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(release)
    sdk = release.sdk_root()
    bt = release.build_tools(sdk)
    adb = sdk / 'platform-tools/adb'

    def shell(*words):
        return run([adb, '-s', args.serial, 'shell', '-n', shlex.join(words)])

    with tempfile.TemporaryDirectory(prefix='hu-provision-', dir=args.output) as temporary:
        work = Path(temporary)
        classes = work / 'classes'
        dex = work / 'dex'
        classes.mkdir()
        dex.mkdir()
        android = sdk / 'platforms/android-36/android.jar'
        manifest = work / 'AndroidManifest.xml'
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{HELPER_PACKAGE}">
  <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="36" />
  <application android:label="Riff HU provisioning" />
  <instrumentation android:name="{HELPER_CLASS}" android:targetPackage="{args.package}" />
</manifest>\n''')
        run(['javac', '-source', '17', '-target', '17', '-cp', android, '-d', classes,
             ROOT / 'tools/hu-backlight/HuConfigurationProvisioner.java'])
        run([bt / 'd8', '--lib', android, '--output', dex,
             classes / 'link/roadio/riff/huprovision/HuConfigurationProvisioner.class'])
        run([bt / 'aapt2', 'link', '-I', android, '--manifest', manifest, '-o', work / 'unsigned.apk'])
        with zipfile.ZipFile(work / 'unsigned.apk', 'a') as apk:
            apk.write(dex / 'classes.dex', 'classes.dex')
        run([bt / 'zipalign', '-f', '-p', '4', work / 'unsigned.apk', work / 'aligned.apk'])
        if args.package.endswith('.instrumented'):
            signing = ['--ks', Path.home() / '.android/debug.keystore', '--ks-key-alias', 'androiddebugkey',
                       '--ks-pass', 'pass:android', '--key-pass', 'pass:android']
        else:
            signing = ['--ks', release.KEYSTORE, '--ks-key-alias', release.ALIAS,
                       '--ks-pass', f'file:{release.STORE_PASSWORD}', '--key-pass', f'file:{release.KEY_PASSWORD}']
        run([bt / 'apksigner', 'sign', *signing, '--out', work / 'helper.apk', work / 'aligned.apk'])
        if args.apk:
            output = run([adb, '-s', args.serial, 'install', '-r', args.apk])
            if 'Success' not in output:
                raise RuntimeError(output)
        if args.reset_data:
            if 'Success' not in shell('pm', 'clear', args.package):
                raise RuntimeError('Explicit reset failed')
        shell('am', 'force-stop', args.package)
        shell('appops', 'set', args.package, 'WRITE_SETTINGS', 'allow')
        run([adb, '-s', args.serial, 'install', '-r', work / 'helper.apk'])
        try:
            output = shell('am', 'instrument', '-w', HELPER_PACKAGE + '/' + HELPER_CLASS)
            (args.output / 'provision.txt').write_text(output)
            prefix = 'INSTRUMENTATION_RESULT: hu_configuration='
            line = next((line for line in output.splitlines() if line.startswith(prefix)), None)
            if line is None or 'provision_failure=' in output:
                raise RuntimeError('Provisioning failed; inspect saved output')
            report = json.loads(line[len(prefix):])
            (args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
            print(json.dumps(report, indent=2))
        finally:
            run([adb, '-s', args.serial, 'uninstall', HELPER_PACKAGE])
            shell('am', 'force-stop', args.package)
            if not args.no_launch:
                shell('am', 'start', '-n', args.package + '/link.roadio.riff.MainActivity')


if __name__ == '__main__':
    main()

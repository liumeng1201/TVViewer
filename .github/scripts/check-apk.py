import argparse
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile


def check_apk(apk_path: Path, variant: str) -> None:
    with ZipFile(apk_path) as apk:
        files = set(apk.namelist())
        for adapter in ("player.js", "cctv.js", "yangshipin.js"):
            if f"assets/playback/{adapter}" not in files:
                raise SystemExit(f"Missing site adapter: {adapter}")

        expected_kernel = "assets/045738_x5.tbs.apk"
        kernels = {name for name in files if name.endswith((".tbs.apk", ".tbs_.apk"))}
        if variant == "standard":
            if kernels:
                raise SystemExit(f"Standard APK contains an unexpected X5 package: {kernels}")
        else:
            if kernels != {expected_kernel}:
                raise SystemExit(f"Expected exactly {expected_kernel}, found {kernels}")
            with ZipFile(BytesIO(apk.read(expected_kernel))) as kernel:
                abis = {
                    name.split("/")[1]
                    for name in kernel.namelist()
                    if name.startswith("lib/") and name.endswith(".so")
                }
                if abis != {"armeabi"}:
                    raise SystemExit(f"Expected a bundled armeabi kernel, found {abis}")
    print(f"{apk_path.name} contains the expected {variant} assets")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("variant", choices=("standard", "x5"))
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()
    apks = list(args.output_dir.glob("*.apk"))
    if len(apks) != 1:
        raise SystemExit(f"Expected one APK in {args.output_dir}, found {len(apks)}")
    check_apk(apks[0], args.variant)


if __name__ == "__main__":
    main()

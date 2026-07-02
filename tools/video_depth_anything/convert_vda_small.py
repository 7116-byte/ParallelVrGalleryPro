"""
Convert Video Depth Anything Small to a mobile model.

This script is intentionally not run by Gradle. It needs a Python environment
with torch, torchvision, onnx, tensorflow, and the upstream
DepthAnything/Video-Depth-Anything source checkout on PYTHONPATH.
"""

from __future__ import annotations

import argparse
import pathlib
import sys


MODEL_CONFIG = {
    "encoder": "vits",
    "features": 64,
    "out_channels": [48, 96, 192, 384],
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", required=True, help="Path to DepthAnything/Video-Depth-Anything checkout")
    parser.add_argument("--checkpoint", required=True, help="Path to video_depth_anything_vits.pth")
    parser.add_argument("--out", required=True, help="Output directory")
    parser.add_argument("--input-size", type=int, default=518)
    parser.add_argument("--frames", type=int, default=32)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    upstream = pathlib.Path(args.upstream).resolve()
    checkpoint = pathlib.Path(args.checkpoint).resolve()
    out_dir = pathlib.Path(args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    if not upstream.exists():
        raise FileNotFoundError(f"Missing upstream checkout: {upstream}")
    if not checkpoint.exists():
        raise FileNotFoundError(f"Missing checkpoint: {checkpoint}")

    sys.path.insert(0, str(upstream))

    import torch  # type: ignore
    from video_depth_anything.video_depth import VideoDepthAnything  # type: ignore

    model = VideoDepthAnything(**MODEL_CONFIG, metric=False)
    state = torch.load(str(checkpoint), map_location="cpu")
    model.load_state_dict(state, strict=True)
    model.eval()

    sample = torch.rand(1, args.frames, 3, args.input_size, args.input_size, dtype=torch.float32)
    onnx_path = out_dir / "video_depth_anything_vits_518.onnx"
    torch.onnx.export(
        model,
        sample,
        str(onnx_path),
        input_names=["frames"],
        output_names=["depth"],
        opset_version=17,
        do_constant_folding=True,
        dynamic_axes=None,
    )
    print(f"Wrote ONNX: {onnx_path}")
    print("Next step: convert ONNX to TF/TFLite with a compatible converter and validate on Android.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

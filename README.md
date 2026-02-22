# DM3D enhanced edition

An enhanced edition of DM3D for 3D deformable mesh segmentation, derived from the original project and extended with workflow-focused baking behavior.

Based on the original DM3D project: https://github.com/PaluchLabUCL/DeformingMesh3D

## What this version adds

- `bake mesh` integration for deformation workflows driven by external energy sampling.
- reversible `bake mesh` and `unbake` behavior without permanent pixel burn-in on the displayed image.
- per-track bake state, so multiple tracks can remain baked at the same time.
- internal field coupling for deformation energy, with track-aware behavior during sampling.
- mode selection with `attract` and `repel`.
- `bake region` options: `fill inside`, `fill outside`, and `outline only`.

## Build from source

Prerequisites:

- Java 8+ (project builds with Java 8 target)
- Maven 3.8+

Build command:

```bash
mvn -DskipTests clean package
```

Expected artifact:

- `target/dm3d-1.5.0-SNAPSHOT.jar`

## Installation in Fiji/ImageJ

Local installation without a release package:

1. Build the project with Maven.
2. Close Fiji/ImageJ if it is running.
3. Copy `target/dm3d-1.5.0-SNAPSHOT.jar` into your Fiji/ImageJ `plugins` directory.
4. Start Fiji/ImageJ.
5. Launch the plugin from the DM3D-related menu entry provided by the jar.

## Quick start

1. Open a 3D image stack in Fiji/ImageJ.
2. Launch DM3D and initialize or load mesh tracks.
3. Select a target track/mesh at the frame you want to process.
4. Choose the mode (`attract` or `repel`) in the bake controls.
5. Select a `bake region` option (`fill inside`, `fill outside`, or `outline only`).
6. Press `bake mesh` to apply baking to the selected track.
7. Run deformation controls; baked tracks stay locked while other tracks can continue.
8. Use `bake mesh` again on that selected baked track to unbake, or use `unbake all` for bulk cleanup.

## bake region modes

- `fill inside`: strengthens the interior region plus outline influence for the selected mesh.
- `fill outside`: strengthens the exterior region plus outline influence for the selected mesh.
- `outline only`: applies outline-focused influence without region fill.

Using `fill inside` or `fill outside` assumes a closed mesh; open or invalid topology can reduce mask quality.

## Notes and limitations

- Baking is energy-path based and intended to affect deformation behavior rather than permanently editing visible pixels.
- Behavior is tied to the baked state stored per track; clearing or replacing track data can invalidate old bake context.
- Image quality and mesh topology still control practical stability of deformation.

## Upstream and credits

This repository is derived from the original DM3D work by its authors and contributors:

- upstream project: https://github.com/PaluchLabUCL/DeformingMesh3D
- reference chapter: https://doi.org/10.1016/bs.mcb.2016.05.003

## License

This project is distributed under the MIT license. See `LICENSE` for the full text.

# DM3D enhanced edition

An enhanced edition of DM3D for 3D deformable mesh segmentation, derived from the original project and extended with workflow-focused baking behaviour.

This repository is derived from the original DM3D project:
https://github.com/PaluchLabUCL/DeformingMesh3D

## what this version adds

- `bake mesh` integration for deformation workflows driven by external energy sampling.
- reversible `bake mesh` and `unbake` behaviour without permanent pixel burn-in on the displayed image.
- per-track bake state so multiple tracks can remain baked at the same time.
- internal field coupling for deformation energy with track-aware behaviour during sampling.
- mode selection with `attract` and `repel`.
- `bake region` options: `fill inside`, `fill outside`, and `outline only`.

## build from source

Prerequisites:
- Java 8+ (build target is Java 8)
- Maven 3.8+

Build:

    mvn -DskipTests clean package

Expected artifact:
- `target/dm3d-1.5.0-SNAPSHOT.jar`

## installation in Fiji/ImageJ

Local installation without a release package:
1. build the project with Maven.
2. close Fiji/ImageJ if it is running.
3. copy `target/dm3d-1.5.0-SNAPSHOT.jar` into your Fiji/ImageJ `plugins` directory.
4. start Fiji/ImageJ.
5. launch the plugin from the DM3D-related menu entry provided by the jar.

## quick start

1. open a 3D image stack in Fiji/ImageJ.
2. launch DM3D and initialise or load mesh tracks.
3. select a target track/mesh at the frame you want to process.
4. choose the mode (`attract` or `repel`) in the bake controls.
5. select a `bake region` option (`fill inside`, `fill outside`, or `outline only`).
6. press `bake mesh` to apply baking to the selected track.
7. run deformation controls; baked tracks stay locked while other tracks can continue.
8. press `bake mesh` again on the selected baked track to unbake, or use `unbake all` for bulk cleanup.

## bake region modes

- `fill inside`: applies interior fill plus outline influence for the selected mesh.
- `fill outside`: applies exterior fill plus outline influence for the selected mesh.
- `outline only`: applies outline-focused influence without region fill.

Using `fill inside` or `fill outside` assumes a closed mesh; open or invalid topology can reduce mask quality.

## notes and limitations

- baking is intended to affect deformation behaviour via internal fields rather than permanently editing visible pixels.
- baked state is stored per track; clearing or replacing track data can invalidate previous bake context.
- mesh topology and image quality still control practical stability of deformation.

## upstream and credits

- upstream project: https://github.com/PaluchLabUCL/DeformingMesh3D
- reference chapter: https://doi.org/10.1016/bs.mcb.2016.05.003

## license

This project is distributed under the MIT license. See `LICENSE` for the full text.
[![Build Status](https://github.com/PaluchLabUCL/DeformingMesh3D/actions/workflows/build.yml/badge.svg)](https://github.com/PaluchLabUCL/DeformingMesh3D/actions/workflows/build.yml)

# DM3D

3D image segmentation for roundish cells.

This repository is based on the original DM3D project:
https://github.com/PaluchLabUCL/DeformingMesh3D

## installation

The easiest installation path is through the Fiji update site.

1. Open Fiji.
2. Go to `help` -> `update...`.
3. In the updater, open `Manage Update Sites`.
4. Add this update site:
   https://sites.imagej.net/Odinsbane
5. Apply updates.

After update, the deforming mesh plugin is available in:
`plugins` -> `PL_Mesh3D` -> `Deforming Mesh 3D`

## documentation

User guides and API docs are available at:
https://franciscrickinstitute.github.io/dm3d-pages/

## scripting quick start

From Fiji, open `file` -> `new` -> `script`, then use Groovy or JavaScript.

```groovy
#@ Dm3dService service
controls = service.getApplicationController()
```

## what is new in this version

Recent updates focus on the bake workflow used by external energy sampling:

- `bake mesh` is integrated into the control workflow with reversible `bake mesh` / `unbake all` behavior.
- per-track bake state is supported so multiple tracks can stay baked at the same time.
- internal bake fields affect deformation energy without permanently editing displayed image pixels.
- bake mode selection supports `attract` and `repel`.
- `bake region` options are standardized as `fill inside`, `fill outside`, and `outline only`.

## credits and licensing

DM3D builds on work by the original DM3D authors and contributors.

License remains MIT; see `LICENSE` for details.

## reference

[Chapter 19 - An active contour ImageJ plugin to monitor daughter cell size in 3D during cytokinesis](https://www.sciencedirect.com/science/article/pii/S0091679X16300607?via%3Dihub)

MB Smith, A Chaigne, EK Paluch

https://doi.org/10.1016/bs.mcb.2016.05.003

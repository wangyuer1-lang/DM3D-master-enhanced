package deformablemesh.gimli2b;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.geometry.DeformableMesh3D;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Arrays;

@Plugin(
        type = Command.class,
        name = "Bake Selected Mesh Outline",
        menuPath = "Plugins > DM3D > Tools > Bake Selected Mesh Outline"
)
public class BakeSelectedMeshOutlineCommand implements Command {
    @Parameter
    private Dm3dService dm3dService;

    @Parameter(label = "Alpha", description = "Amount added at outline voxels.")
    private double alpha = 50;

    @Parameter(label = "Outline Thickness (voxels)", description = "6-connected dilation steps.", min = "1")
    private int thickness = 1;

    @Parameter(label = "Show Outline Stack")
    private boolean showOutlineStack = false;

    @Override
    public void run() {
        SegmentationController controller = dm3dService.getApplicationController();
        if (controller == null) {
            IJ.showMessage("DM3D controller is not available.");
            return;
        }

        DeformableMesh3D selectedMesh = controller.getSelectedMesh();
        if (selectedMesh == null) {
            IJ.showMessage("No selected mesh.");
            return;
        }

        MeshImageStack meshImageStack = controller.getMeshImageStack();
        if (meshImageStack == null || meshImageStack.getOriginalPlus() == null) {
            IJ.showMessage("No image is loaded.");
            return;
        }

        ImagePlus mask = DeformableMesh3DTools.createBinaryRepresentation(meshImageStack, selectedMesh);
        ImagePlus outline = createOutline(mask, Math.max(1, thickness));
        ImagePlus baked = bake(meshImageStack.getCurrentFrame(), outline, alpha);

        if (showOutlineStack) {
            outline.setTitle(meshImageStack.getShortTitle() + "-selected-mesh-outline");
            outline.show();
        }

        baked.show();
    }

    private ImagePlus createOutline(ImagePlus mask, int thicknessVoxels) {
        int width = mask.getWidth();
        int height = mask.getHeight();
        int depth = mask.getNSlices();
        int sliceSize = width * height;
        int voxels = sliceSize * depth;

        boolean[] inside = new boolean[voxels];
        ImageStack maskStack = mask.getStack();
        for (int z = 0; z < depth; z++) {
            byte[] pixels = (byte[]) maskStack.getProcessor(z + 1).convertToByteProcessor().getPixels();
            int offset = z * sliceSize;
            for (int i = 0; i < sliceSize; i++) {
                inside[offset + i] = (pixels[i] & 0xff) > 0;
            }
        }

        boolean[] outlineMask = new boolean[voxels];
        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = x + y * width + z * sliceSize;
                    if (!inside[index]) {
                        continue;
                    }
                    if (hasOutsideNeighbor(inside, width, height, depth, x, y, z)) {
                        outlineMask[index] = true;
                    }
                }
            }
        }

        boolean[] dilated = Arrays.copyOf(outlineMask, outlineMask.length);
        for (int iteration = 1; iteration < thicknessVoxels; iteration++) {
            boolean[] next = Arrays.copyOf(dilated, dilated.length);
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int index = x + y * width + z * sliceSize;
                        if (!dilated[index]) {
                            continue;
                        }
                        if (x > 0) next[index - 1] = true;
                        if (x < width - 1) next[index + 1] = true;
                        if (y > 0) next[index - width] = true;
                        if (y < height - 1) next[index + width] = true;
                        if (z > 0) next[index - sliceSize] = true;
                        if (z < depth - 1) next[index + sliceSize] = true;
                    }
                }
            }
            dilated = next;
        }

        ImageStack outlineStack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            byte[] pixels = new byte[sliceSize];
            int offset = z * sliceSize;
            for (int i = 0; i < sliceSize; i++) {
                if (dilated[offset + i]) {
                    pixels[i] = (byte) 255;
                }
            }
            outlineStack.addSlice(new ByteProcessor(width, height, pixels));
        }

        ImagePlus outline = mask.createImagePlus();
        outline.setStack(outlineStack, 1, depth, 1);
        outline.setTitle(mask.getTitle() + "-outline");
        return outline;
    }

    private boolean hasOutsideNeighbor(boolean[] inside, int width, int height, int depth, int x, int y, int z) {
        return !isInside(inside, width, height, depth, x - 1, y, z)
                || !isInside(inside, width, height, depth, x + 1, y, z)
                || !isInside(inside, width, height, depth, x, y - 1, z)
                || !isInside(inside, width, height, depth, x, y + 1, z)
                || !isInside(inside, width, height, depth, x, y, z - 1)
                || !isInside(inside, width, height, depth, x, y, z + 1);
    }

    private boolean isInside(boolean[] inside, int width, int height, int depth, int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
            return false;
        }
        int sliceSize = width * height;
        int index = x + y * width + z * sliceSize;
        return inside[index];
    }

    private ImagePlus bake(ImagePlus original, ImagePlus outline, double outlineAlpha) {
        int width = original.getWidth();
        int height = original.getHeight();
        int depth = Math.min(original.getNSlices(), outline.getNSlices());
        int sliceSize = width * height;
        double alphaValue = Math.max(0, outlineAlpha);

        ImageStack bakedStack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            FloatProcessor proc = original.getStack().getProcessor(z + 1).convertToFloatProcessor();
            float[] originalPixels = (float[]) proc.getPixels();
            byte[] outlinePixels = (byte[]) outline.getStack().getProcessor(z + 1).convertToByteProcessor().getPixels();

            for (int i = 0; i < sliceSize; i++) {
                int outlineValue = outlinePixels[i] & 0xff;
                originalPixels[i] += (float) (alphaValue * (outlineValue / 255.0));
            }
            bakedStack.addSlice(original.getStack().getSliceLabel(z + 1), proc);
        }

        ImagePlus baked = original.createImagePlus();
        baked.setStack(bakedStack, 1, depth, 1);
        baked.setTitle(original.getTitle() + "-baked-selected-mesh-outline");
        baked.setCalibration(original.getCalibration().copy());
        return baked;
    }
}

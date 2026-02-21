package deformablemesh.util;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import ij.gui.ImageRoi;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;

import java.util.ArrayList;
import java.util.List;

public class MeshBakeUtils {
    private MeshBakeUtils(){
    }

    public static boolean[] createOutlineMask(MeshImageStack stack, DeformableMesh3D mesh){
        ImagePlus mask = DeformableMesh3DTools.createBinaryRepresentation(stack, mesh);
        return createOutlineMask(mask);
    }

    public static void applyOutlineToStackData(MeshImageStack stack, boolean[] outline){
        double bakeValue = getBakeValue(stack);
        int count = Math.min(outline.length, stack.data.length);
        for(int i = 0; i < count; i++){
            if(outline[i]){
                stack.data[i] = bakeValue;
            }
        }
    }

    private static double getBakeValue(MeshImageStack stack){
        switch(stack.getType()){
            case MeshImageStack.INT8:
                return 255.0;
            case MeshImageStack.INT16:
                return 65535.0;
            case MeshImageStack.FLOAT32:
                return Math.max(1_000_000.0, stack.getMaxValue());
            default:
                return Math.max(255.0, stack.getMaxValue());
        }
    }

    private static boolean[] createOutlineMask(ImagePlus mask){
        int width = mask.getWidth();
        int height = mask.getHeight();
        int depth = mask.getNSlices();
        int sliceSize = width * height;
        int voxels = sliceSize * depth;
        boolean[] inside = new boolean[voxels];
        boolean[] outline = new boolean[voxels];

        ImageStack stack = mask.getStack();
        for(int z = 0; z < depth; z++){
            byte[] pixels = (byte[])stack.getProcessor(z + 1).convertToByteProcessor().getPixels();
            int offset = z * sliceSize;
            for(int i = 0; i < sliceSize; i++){
                inside[offset + i] = (pixels[i] & 0xff) > 0;
            }
        }

        for(int z = 0; z < depth; z++){
            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){
                    int index = x + y * width + z * sliceSize;
                    if(!inside[index]){
                        continue;
                    }
                    if(isOutside(inside, width, height, depth, x - 1, y, z)
                            || isOutside(inside, width, height, depth, x + 1, y, z)
                            || isOutside(inside, width, height, depth, x, y - 1, z)
                            || isOutside(inside, width, height, depth, x, y + 1, z)
                            || isOutside(inside, width, height, depth, x, y, z - 1)
                            || isOutside(inside, width, height, depth, x, y, z + 1)){
                        outline[index] = true;
                    }
                }
            }
        }

        return outline;
    }

    public static List<ImageRoi> createOutlineOverlayRois(
            boolean[] outline,
            int width,
            int height,
            int depth,
            int frame,
            int channel
    ){
        int sliceSize = width * height;
        List<ImageRoi> rois = new ArrayList<>();
        for(int z = 0; z < depth; z++){
            int offset = z * sliceSize;
            byte[] pixels = new byte[sliceSize];
            boolean hasOutline = false;
            for(int i = 0; i < sliceSize; i++){
                if(outline[offset + i]){
                    pixels[i] = (byte)255;
                    hasOutline = true;
                }
            }
            if(!hasOutline){
                continue;
            }
            ByteProcessor bp = new ByteProcessor(width, height, pixels, null);
            ImageRoi roi = new ImageRoi(0, 0, bp);
            roi.setZeroTransparent(true);
            roi.setOpacity(0.45);
            roi.setPosition(channel + 1, z + 1, frame + 1);
            rois.add(roi);
        }
        return rois;
    }

    private static boolean isOutside(boolean[] inside, int width, int height, int depth, int x, int y, int z){
        if(x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth){
            return true;
        }
        int sliceSize = width * height;
        int index = x + y * width + z * sliceSize;
        return !inside[index];
    }
}

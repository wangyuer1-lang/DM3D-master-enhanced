package deformablemesh.util;

import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

public class MeshBakeUtils {
    private MeshBakeUtils(){
    }

    public static List<Object> copyFramePixels(MeshImageStack stack, int frame, int channel){
        ImageStack imageStack = stack.getOriginalPlus().getStack();
        int slices = stack.getNSlices();
        int channels = stack.getNChannels();
        List<Object> copies = new ArrayList<>(slices);
        for(int z = 0; z < slices; z++){
            int stackIndex = z * channels + frame * channels * slices + channel + 1;
            ImageProcessor proc = imageStack.getProcessor(stackIndex);
            copies.add(copyPixels(proc.getPixels()));
        }
        return copies;
    }

    public static void restoreFramePixels(MeshImageStack stack, int frame, int channel, List<Object> pixels){
        ImageStack imageStack = stack.getOriginalPlus().getStack();
        int slices = stack.getNSlices();
        int channels = stack.getNChannels();
        int restoredSlices = Math.min(slices, pixels.size());
        for(int z = 0; z < restoredSlices; z++){
            int stackIndex = z * channels + frame * channels * slices + channel + 1;
            ImageProcessor proc = imageStack.getProcessor(stackIndex);
            proc.setPixels(copyPixels(pixels.get(z)));
        }
        stack.copyValues();
    }

    public static void bakeSelectedMeshOutline(
            MeshImageStack stack,
            DeformableMesh3D mesh,
            int frame,
            int channel,
            double alpha
    ){
        ImagePlus mask = DeformableMesh3DTools.createBinaryRepresentation(stack, mesh);
        boolean[] outline = createOutlineMask(mask);
        applyOutlineToFrame(stack, frame, channel, outline, alpha);
    }

    private static void applyOutlineToFrame(MeshImageStack stack, int frame, int channel, boolean[] outline, double alpha){
        int width = stack.getWidthPx();
        int height = stack.getHeightPx();
        int slices = stack.getNSlices();
        int channels = stack.getNChannels();
        int sliceSize = width * height;
        ImageStack imageStack = stack.getOriginalPlus().getStack();

        for(int z = 0; z < slices; z++){
            int stackIndex = z * channels + frame * channels * slices + channel + 1;
            ImageProcessor proc = imageStack.getProcessor(stackIndex);
            float bakeValue = getBakeValue(proc, stack.getType());
            int offset = z * sliceSize;
            for(int y = 0; y < height; y++){
                int row = offset + y * width;
                for(int x = 0; x < width; x++){
                    if(outline[row + x]){
                        proc.setf(x, y, bakeValue);
                    }
                }
            }
        }
        stack.copyValues();
    }

    private static float getBakeValue(ImageProcessor proc, int type){
        switch(type){
            case MeshImageStack.INT8:
                return 255f;
            case MeshImageStack.INT16:
                return 65535f;
            case MeshImageStack.FLOAT32:
                return Math.max(1_000_000f, getSliceMax(proc));
            default:
                return Math.max(255f, getSliceMax(proc));
        }
    }

    private static float getSliceMax(ImageProcessor proc){
        int width = proc.getWidth();
        int height = proc.getHeight();
        float max = -Float.MAX_VALUE;
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                float value = proc.getf(x, y);
                if(value > max){
                    max = value;
                }
            }
        }
        return max;
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

    private static boolean isOutside(boolean[] inside, int width, int height, int depth, int x, int y, int z){
        if(x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth){
            return true;
        }
        int sliceSize = width * height;
        int index = x + y * width + z * sliceSize;
        return !inside[index];
    }

    private static Object copyPixels(Object pixels){
        if(pixels instanceof byte[]){
            return ((byte[])pixels).clone();
        } else if(pixels instanceof short[]){
            return ((short[])pixels).clone();
        } else if(pixels instanceof float[]){
            return ((float[])pixels).clone();
        } else if(pixels instanceof int[]){
            return ((int[])pixels).clone();
        }
        throw new IllegalArgumentException("Unsupported pixel type: " + pixels.getClass().getName());
    }
}

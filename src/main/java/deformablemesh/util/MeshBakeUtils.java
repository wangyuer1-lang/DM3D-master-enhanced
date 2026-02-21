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
    private static final double[] DEFAULT_SHELL_FACTORS = new double[]{1.0, 0.85, 0.72, 0.60, 0.50, 0.42};

    private MeshBakeUtils(){
    }

    public static boolean[] createOutlineMask(MeshImageStack stack, DeformableMesh3D mesh){
        ImagePlus mask = DeformableMesh3DTools.createBinaryRepresentation(stack, mesh);
        return createOutlineMask(mask);
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

    public static double[] createBakeTargetField(MeshImageStack stack, DeformableMesh3D mesh, int shells){
        boolean[] outline = createOutlineMask(stack, mesh);
        int shellCount = shells > 0 ? shells : DEFAULT_SHELL_FACTORS.length;
        double peak = getBakeValue(stack);
        int width = stack.getWidthPx();
        int height = stack.getHeightPx();
        int depth = stack.getNSlices();
        int sliceSize = width*height;
        int total = sliceSize*depth;
        double[] target = new double[total];
        boolean[] visited = new boolean[total];

        List<Integer> frontier = new ArrayList<>();
        for(int i = 0; i<total; i++){
            if(outline[i]){
                visited[i] = true;
                frontier.add(i);
            }
        }

        for(int shell = 0; shell<shellCount && !frontier.isEmpty(); shell++){
            double factor = getShellFactor(shell);
            double shellValue = peak*factor;
            for(Integer idx: frontier){
                if(shellValue > target[idx]){
                    target[idx] = shellValue;
                }
            }
            if(shell == shellCount - 1){
                break;
            }
            List<Integer> nextFrontier = new ArrayList<>();
            for(Integer idx: frontier){
                int z = idx/sliceSize;
                int rem = idx - z*sliceSize;
                int y = rem/width;
                int x = rem - y*width;
                addNeighbor(x - 1, y, z, width, height, depth, visited, nextFrontier);
                addNeighbor(x + 1, y, z, width, height, depth, visited, nextFrontier);
                addNeighbor(x, y - 1, z, width, height, depth, visited, nextFrontier);
                addNeighbor(x, y + 1, z, width, height, depth, visited, nextFrontier);
                addNeighbor(x, y, z - 1, width, height, depth, visited, nextFrontier);
                addNeighbor(x, y, z + 1, width, height, depth, visited, nextFrontier);
            }
            frontier = nextFrontier;
        }

        return target;
    }

    private static double getShellFactor(int shell){
        if(shell < DEFAULT_SHELL_FACTORS.length){
            return DEFAULT_SHELL_FACTORS[shell];
        }
        double tail = DEFAULT_SHELL_FACTORS[DEFAULT_SHELL_FACTORS.length - 1];
        int extra = shell - DEFAULT_SHELL_FACTORS.length + 1;
        return tail/Math.pow(1.5, extra);
    }

    private static void addNeighbor(
            int x, int y, int z,
            int width, int height, int depth,
            boolean[] visited,
            List<Integer> next
    ){
        if(x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth){
            return;
        }
        int idx = x + y*width + z*width*height;
        if(visited[idx]){
            return;
        }
        visited[idx] = true;
        next.add(idx);
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

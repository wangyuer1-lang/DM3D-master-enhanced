package deformablemesh.geometry.meshgeneration;

import deformablemesh.util.connectedcomponents.Region;

import java.util.List;

public interface RegionGenerator {
    List<Region> generateRegions();
}

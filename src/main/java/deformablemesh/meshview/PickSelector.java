/*-
 * #%L
 * Triangulated surface for deforming in 3D.
 * %%
 * Copyright (C) 2013 - 2023 University College London
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package deformablemesh.meshview;

import deformablemesh.SegmentationController;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.track.Track;
import org.jogamp.java3d.GeometryArray;
import org.jogamp.java3d.utils.picking.PickResult;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by smithm3 on 24/05/18.
 */
public class PickSelector implements CanvasView {
    SegmentationController controller;
    private static final int CLICK_RESET_THRESHOLD_PX = 3;
    private int lastClickX = Integer.MIN_VALUE;
    private int lastClickY = Integer.MIN_VALUE;
    private int cycleIndex = 0;
    private List<DeformableMesh3D> lastCandidates = new ArrayList<>();

    public PickSelector(SegmentationController c){
        this.controller = c;
    }

    public void updatePick(PickResult[] results, MouseEvent evt, boolean clicked) {
        if(clicked) {
            int frame = controller.getCurrentFrame();
            List<DeformableMesh3D> candidates = collectCandidates(results, frame);
            if(candidates.isEmpty()){
                return;
            }
            boolean sameClickLocation = isSameClickLocation(evt);
            if(!sameClickLocation || !sameCandidates(candidates)){
                cycleIndex = 0;
            }
            int selectedIndex = Math.floorMod(cycleIndex, candidates.size());
            if(evt.isShiftDown()){
                selectedIndex = Math.floorMod(selectedIndex + 1, candidates.size());
            }
            controller.selectMesh(candidates.get(selectedIndex));
            cycleIndex = Math.floorMod(selectedIndex + 1, candidates.size());
            lastClickX = evt.getX();
            lastClickY = evt.getY();
            lastCandidates = new ArrayList<>(candidates);
        }

    }

    private boolean isSameClickLocation(MouseEvent evt){
        if(lastClickX == Integer.MIN_VALUE || lastClickY == Integer.MIN_VALUE){
            return false;
        }
        return Math.abs(evt.getX() - lastClickX) <= CLICK_RESET_THRESHOLD_PX
                && Math.abs(evt.getY() - lastClickY) <= CLICK_RESET_THRESHOLD_PX;
    }

    private boolean sameCandidates(List<DeformableMesh3D> candidates){
        if(candidates.size() != lastCandidates.size()){
            return false;
        }
        for(int i = 0; i < candidates.size(); i++){
            if(candidates.get(i) != lastCandidates.get(i)){
                return false;
            }
        }
        return true;
    }

    private List<DeformableMesh3D> collectCandidates(PickResult[] results, int frame){
        Set<DeformableMesh3D> ordered = new LinkedHashSet<>();
        for(PickResult result: results){
            GeometryArray array = result.getGeometryArray();
            for(Track track: controller.getAllTracks()){
                if(!track.containsKey(frame)){
                    continue;
                }
                DeformableMesh3D mesh = track.getMesh(frame);
                if(mesh.data_object == null || mesh.data_object.surface_object == null){
                    continue;
                }
                if(mesh.data_object.lines == array || mesh.data_object.surface_object.getGeometry() == array){
                    ordered.add(mesh);
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    @Override
    public void updatePressed(PickResult[] results, MouseEvent evt) {

    }

    @Override
    public void updateReleased(PickResult[] results, MouseEvent evt) {

    }

    @Override
    public void updateClicked(PickResult[] results, MouseEvent evt) {
        updatePick(results, evt, true);

    }

    @Override
    public void updateMoved(PickResult[] results, MouseEvent evt) {
        updatePick(results, evt, false);
    }

    @Override
    public void updateDragged(PickResult[] results, MouseEvent evt) {

    }
}

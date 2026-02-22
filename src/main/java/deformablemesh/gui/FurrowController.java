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
package deformablemesh.gui;

import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.geometry.Furrow3D;
import deformablemesh.geometry.FurrowManageModel;
import deformablemesh.geometry.FurrowTransformer;
import deformablemesh.geometry.Intersection;
import deformablemesh.geometry.interceptable.InterceptingMesh3D;
import deformablemesh.geometry.modifier.MeshModifier;
import deformablemesh.geometry.projectable.ProjectableMesh;
import deformablemesh.io.FurrowWriter;
import deformablemesh.track.Track;
import ij.process.ImageProcessor;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Input containing a histogram for determining the threshold and a sliced view
 * of the current selected image. Can be used for both interacting with the
 * currently selected mesh modifier and selecting meshes.
 *
 * Created by msmith on 4/14/14.
 */
public class FurrowController implements FrameListener, ListDataListener {
    private static class DepthHit{
        final DeformableMesh3D mesh;
        final double depth;

        DepthHit(DeformableMesh3D mesh, double depth){
            this.mesh = mesh;
            this.depth = depth;
        }
    }

    final FurrowManageModel furrowManager;
    public SegmentationController model;
    MouseAdapter currentControls;

    static class DoubleValue{
        double value;
        public DoubleValue(double v){ this.value = v;}
        public void setValue(double v){ value = v;}
        public double getValue(){ return value; };
    }
    DoubleValue px = new DoubleValue(0);
    DoubleValue py = new DoubleValue(0);
    DoubleValue pz = new DoubleValue(0);
    DoubleValue dx = new DoubleValue(0);
    DoubleValue dy = new DoubleValue(0);
    DoubleValue dz = new DoubleValue(1);

    double thresh = 128;
    JLabel frame;
    int currentFrame;
    private Slice3DView sliceView;

    HistogramInput histControls;
    JPanel contentPane;
    FurrowInput furrowInput;
    JFrame parent;
    List<FrameListener> listeners = new ArrayList<>();
    List<ProjectableMesh> selectableMeshes = new ArrayList<>();
    boolean furrowShowing;
    private boolean showTexture;

    MeshModifier modifier;

    public FurrowController(SegmentationController model){
        this.model = model;
        furrowManager = new FurrowManageModel();
        sliceView = new Slice3DView();
        activateSelectMeshMode();
    }

    public void setCursorRadius(double r) {
        if(modifier == null) return;
        modifier.setCursorRadius(r);
    }

    public double getCursorRadius(){
        if(modifier == null) return -1;
        return modifier.getCursorRadius();
    }

    public void showFurrow(boolean textured){
        furrowShowing = true;
        showTexture = textured;
        setFurrowValues();
    }

    public void hideFurrow(){
        furrowShowing = false;
        Furrow3D furrow = getFurrow();
        if(furrow != null){
            furrow.removeDataObject();
            frameChanged(model.getCurrentFrame());
        }

    }
    private final ReentrantLock modifierLock = new ReentrantLock();
    private void grab(){
        modifierLock.lock();
    }
    private void release(){
        modifierLock.unlock();
    }
    public boolean modifyingMesh(){
        return modifier != null;
    }

    /**
     * Starts the select nodes activity.
     *
     * TODO: Select nodes either begins modification, or just changes the mode.
     *
     * @return If this starts a new modifier or just changes the mode.
     */
    public boolean selectNodes(){
        if(model.getSelectedMesh() == null){
            return false;
        }
        if(model.isSelectedTrackBaked()){
            ij.IJ.error("Mesh is baked/locked. Unbake to deform.");
            return false;
        }
        if(modifier == null){
            initializeModifier();
            return true;
        }
        modifier.setSelectNodesMode();
        return false;
    }
    void initializeModifier(){
        modifier = new MeshModifier();
        modifier.setMeshFrame3D(model.getMeshFrame3D());
        modifier.setFurrow(getFurrow());
        modifier.setMeshImageStack(model.getMeshImageStack());
        modifier.activate3DFramePicker();


        sliceView.addDrawable( modifier );
        setSliceListener(new MouseAdapter(){
            @Override
            public void mousePressed(MouseEvent evt){
                double[] pt = getNormalizedVolumeCoordiante(evt.getPoint());
                modifier.updatePressed(pt, evt);
                sliceView.repaint();
            }
            @Override
            public void mouseReleased(MouseEvent evt){
                double[] pt = getNormalizedVolumeCoordiante(evt.getPoint());
                modifier.updateReleased(pt, evt);
                sliceView.repaint();
            }
            @Override
            public void mouseClicked(MouseEvent evt){
                double[] pt = getNormalizedVolumeCoordiante(evt.getPoint());
                modifier.updateClicked(pt, evt);
                sliceView.repaint();
            }
            @Override
            public void mouseMoved(MouseEvent evt){
                double[] pt = getNormalizedVolumeCoordiante(evt.getPoint());
                modifier.updateMoved(pt, evt);
                sliceView.repaint();
            }
            @Override
            public void mouseDragged(MouseEvent evt){
                double[] pt = getNormalizedVolumeCoordiante(evt.getPoint());
                modifier.updateDragged(pt, evt);
                sliceView.repaint();
            }
        });
        modifier.setMesh( model.getSelectedMesh() );

        grab();
        model.submit(()->{
            grab();
            release();
        });
    }
    public void sculptClicked(){
        if(model.getSelectedMesh() == null){
            return;
        }
        if(model.isSelectedTrackBaked()){
            ij.IJ.error("Mesh is baked/locked. Unbake to deform.");
            return;
        }
        if(modifier==null ) initializeModifier();
        modifier.setSculptMode();
    }

    public void translateClicked(){
        if(model.getSelectedMesh() == null){
            return;
        }
        if(model.isSelectedTrackBaked()){
            ij.IJ.error("Mesh is baked/locked. Unbake to deform.");
            return;
        }
        if(modifier == null) initializeModifier();
        modifier.setTranslateMode();
    }

    public JPanel getHistControlsPanel(){
        if(histControls == null){
            histControls = new HistogramInput(this);
        }
        return histControls.panel;
    }

    public void finishedClicked(){
        if(modifier==null) return;

        modifier.deactivate();
        DeformableMesh3D original = modifier.getOriginalMesh();
        Track host = model.getAllTracks().stream().filter(t->t.containsMesh(original)).findFirst().orElse(null);
        if(model.isTrackBaked(host)){
            ij.IJ.error("Mesh is baked/locked. Unbake to deform.");
            sliceView.removeDrawable(modifier);
            modifier = null;
            activateSelectMeshMode();
            release();
            sliceView.repaint();
            return;
        }
        if(host != null){
            int frame = host.getFrame(original);
            model.setMesh(host, frame, modifier.getMesh());
        }
        sliceView.removeDrawable(modifier);
        modifier = null;
        activateSelectMeshMode();
        release();
    }
    public void cancel(){
        if(modifier==null) return;
        modifier.deactivate();
        sliceView.removeDrawable(modifier);
        modifier = null;
        activateSelectMeshMode();
        release();
        sliceView.repaint();
    }

    public boolean isTextureShowing(){
        return showTexture;
    }


    public Slice3DView getSliceView(){
        return sliceView;
    }

    double[] getNormalizedVolumeCoordiante(Point p){
        FurrowTransformer t = new FurrowTransformer(getFurrow(), model.getMeshImageStack());
        Point2D furrowPos = sliceView.getScaledLocation(p);
        double[] pt = t.getVolumeCoordinates(
                new double[]{furrowPos.getX(), furrowPos.getY()});
        return pt;
    }

    /**
     * Creates a furrow input that is tied to this ring controller.
     *
     *
     * @return
     */
    public FurrowInput createFurrowInput(){
        furrowInput = new FurrowInput();

        furrowInput.addPlaneChangeListener(new FurrowInput.PlaneChangeListener(){

            @Override
            public void setNormal(double[] n) {
                double[] pos = getInputPosition();
                setFurrow(n, pos);
            }

            @Override
            public void updatePosition(double dx, double dy, double dz) {
                double[] pos = getInputPosition();
                pos[0] += dx;
                pos[1] += dy;
                pos[2] += dz;
                setFurrow(getInputNormal(), pos);
            }
        });


        return furrowInput;
    }

    public void setSliceListener( MouseAdapter adapter){

        if(adapter == currentControls){
            return;
        } else if(currentControls != null){
            sliceView.removeMouseAdapter(currentControls);
        }

        sliceView.addMouseAdapter(adapter);
        currentControls = adapter;
    }

    public void activateSelectMeshMode(){

        setSliceListener( new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {

                Furrow3D furrow = getFurrow();

                if(furrow == null) return;

                FurrowTransformer t = new FurrowTransformer(
                        furrow, model.getMeshImageStack()
                );

                Point2D pt = sliceView.getScaledLocation(e.getPoint());
                double[] rayOrigin = t.getVolumeCoordinates(new double[]{pt.getX(), pt.getY()});
                double[] rayDirection = normalizeDirection(furrow.normal);
                List<DeformableMesh3D> candidates = collectSliceSelectionCandidates(t, pt, rayOrigin, rayDirection);
                if(candidates.isEmpty()){
                    return;
                }
                DeformableMesh3D selected = model.chooseCycledSelection(
                        "2d-slice",
                        e.getX(),
                        e.getY(),
                        e.isShiftDown(),
                        candidates
                );
                if(selected != null){
                    model.selectMesh(selected);
                    sliceView.repaint();
                }
            }
        });
    }

    private List<DeformableMesh3D> collectSliceSelectionCandidates(
            FurrowTransformer transformer,
            Point2D clickPoint,
            double[] rayOrigin,
            double[] rayDirection
    ){
        List<DepthHit> hits = new ArrayList<>();
        for(ProjectableMesh projectable: selectableMeshes){
            Shape shape = projectable.continuousPaths(transformer);
            if(!shape.contains(clickPoint)){
                continue;
            }
            DeformableMesh3D mesh = projectable.getMesh();
            InterceptingMesh3D intercepting = new InterceptingMesh3D(mesh);
            List<Intersection> intersections = intercepting.getIntersections(rayOrigin, rayDirection);
            if(intersections.isEmpty()){
                continue;
            }
            double nearestPositiveDepth = intersections.stream()
                    .mapToDouble(intersection -> projectedDistance(rayOrigin, rayDirection, intersection.location))
                    .filter(depth -> depth >= 0)
                    .min()
                    .orElse(Double.POSITIVE_INFINITY);
            if(Double.isInfinite(nearestPositiveDepth)){
                nearestPositiveDepth = intersections.stream()
                        .mapToDouble(intersection -> Math.abs(projectedDistance(rayOrigin, rayDirection, intersection.location)))
                        .min()
                        .orElse(Double.POSITIVE_INFINITY);
            }
            if(!Double.isInfinite(nearestPositiveDepth)){
                hits.add(new DepthHit(mesh, nearestPositiveDepth));
            }
        }
        hits.sort(Comparator.comparingDouble(hit -> hit.depth));
        List<DeformableMesh3D> ordered = new ArrayList<>(hits.size());
        for(DepthHit hit: hits){
            if(!ordered.contains(hit.mesh)){
                ordered.add(hit.mesh);
            }
        }
        return ordered;
    }

    private double projectedDistance(double[] origin, double[] direction, double[] point){
        double dx = point[0] - origin[0];
        double dy = point[1] - origin[1];
        double dz = point[2] - origin[2];
        return dx*direction[0] + dy*direction[1] + dz*direction[2];
    }

    private double[] normalizeDirection(double[] direction){
        double mag = Math.sqrt(direction[0]*direction[0] + direction[1]*direction[1] + direction[2]*direction[2]);
        if(mag == 0){
            return new double[]{0, 0, 1};
        }
        return new double[]{direction[0]/mag, direction[1]/mag, direction[2]/mag};
    }



    void syncSliceViewBoxController(){
        sliceView.repaint();
    }

    public double[] getInputNormal(){
        double[] dir = new double[]{
                dx.getValue(),
                dy.getValue(),
                dz.getValue()
        };

        double d = dir[0]*dir[0] + dir[1]*dir[1] + dir[2]*dir[2];
        d = Math.sqrt(d);
        dir[0] = dir[0]/d;
        dir[1] = dir[1]/d;
        dir[2] = dir[2]/d;

        return dir;
    }

    public double[] getInputPosition(){
        double[] pos = new double[]{
                px.getValue(),
                py.getValue(),
                pz.getValue()
        };
        return pos;
    }

    public void setFurrowValues(){
        double[] dir = getInputNormal();

        dx.setValue(dir[0]);
        dy.setValue(dir[1]);
        dz.setValue(dir[2]);

        double[] pos = getInputPosition();

        setFurrow(dir, pos);

    }


    public void setFrame(int frame){
        currentFrame = frame;
        furrowManager.setFrame(frame);
        ImageProcessor p = furrowManager.getFurrowSlice();
        if(p!=null){
            sliceView.clear();
            Furrow3D furrow = furrowManager.getFurrow(frame);
            if(furrow !=null ) {
                //manage mesh drawing!
                List<ProjectableMesh> meshes = model.getAllTracks().stream().filter(
                        t->t.containsKey(frame)
                ).map(
                        t -> t.getMesh(frame)
                ).map(ProjectableMesh::new).collect(Collectors.toList());
                selectableMeshes.clear();
                selectableMeshes.addAll(meshes);

                List<Drawable> projections = meshes.stream().map(pm -> {
                    return new Drawable() {
                        @Override
                        public void draw(Graphics2D g2d) {
                            FurrowTransformer ft = new FurrowTransformer(
                                    furrow, model.getMeshImageStack()
                            );
                            Shape shape = pm.getProjection( ft );

                            DeformableMesh3D selected = model.getSelectedMesh();
                            if(pm.getMesh() == selected){
                                g2d.setColor(GuiTools.SELECTED_MESH_COLOR);
                                g2d.draw(shape);
                            }else{
                                g2d.setColor(pm.getColor());
                                g2d.draw(shape);
                            }
                        }
                    };
                }).collect(Collectors.toList());
                sliceView.addDrawables(projections);
            }
            histControls.refresh(p);
            sliceView.setSlice(p.getBufferedImage());
            furrowManager.setThresh(thresh);
            ImageProcessor b = furrowManager.createBinarySlice();
            sliceView.setBinary(b.getBufferedImage());
            if(modifier != null){
                sliceView.addDrawable(modifier);
            }
            refreshFurrow();
        }

    }


    public void setThreshold(double v){
        thresh = v;
        furrowManager.setThresh(v);
        ImageProcessor b = furrowManager.createBinarySlice();
        sliceView.setBinary(b.getBufferedImage());
    }
    public void setFurrow(int frame, Furrow3D furrow) {
        double[] center = furrow.cm;
        double[] normal = furrow.normal;
        dx.setValue(normal[0]);
        dy.setValue(normal[1]);
        dz.setValue(normal[2]);

        px.setValue(center[0]);
        py.setValue(center[1]);
        pz.setValue(center[2]);

        furrowManager.putFurrow(frame, furrow);
        frameChanged(model.getCurrentFrame());
    }

    public Furrow3D getFurrow() {
        return furrowManager.getFurrow();
    }

    public Furrow3D getFurrow(int i){
        return furrowManager.getFurrow(i);
    }

    public void writeFurrows(File f, MeshImageStack stack){
        FurrowWriter.writeFurrows(f, stack, furrowManager);
    }

    public void refreshFurrow(){
        Furrow3D f = furrowManager.getFurrow(currentFrame);
        if(f==null){
            return;
        }
        px.setValue(f.cm[0]);
        py.setValue(f.cm[1]);
        pz.setValue(f.cm[2]);
        dx.setValue(f.normal[0]);
        dy.setValue(f.normal[1]);
        dz.setValue(f.normal[2]);
        furrowInput.setFurrow(f);
    }

    public void setFurrow(double[] dir, double[] pos){
        Furrow3D furrow = furrowManager.getFurrow();
        if(furrow!=null) {
            furrow.showTexture(showTexture);
            furrow.setGeometry( pos, dir);
        } else{
            furrow = new Furrow3D(pos, dir);
            furrow.showTexture(showTexture);
            furrowManager.putFurrow(model.getCurrentFrame(), furrow);
        }
        frameChanged(model.getCurrentFrame());
    }

    @Override
    public void frameChanged(int i) {
        setFrame(i);
        for(FrameListener listener: listeners){
            listener.frameChanged(i);
        }
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        contentsChanged(e);
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        contentsChanged(e);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        syncSliceViewBoxController();
    }

    public Map<Integer, Furrow3D> getFurrows() {
        return furrowManager.getFurrows();
    }

    public void setStack(MeshImageStack stack) {
        furrowManager.setImageStack(stack);
    }

    public void addFrameListener(FrameListener listener){
        listeners.add(listener);
    }

    public double getThresh() {

        return thresh;
    }

    public boolean isFurrowShowing() {
        return furrowShowing;
    }
}

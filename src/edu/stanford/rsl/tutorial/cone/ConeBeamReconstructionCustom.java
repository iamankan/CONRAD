package edu.stanford.rsl.tutorial.cone;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageJ;
import edu.stanford.rsl.conrad.data.numeric.Grid3D;
import edu.stanford.rsl.conrad.geometry.trajectories.Trajectory;
import edu.stanford.rsl.conrad.utils.Configuration;
import edu.stanford.rsl.conrad.utils.ImageUtil;
import edu.stanford.rsl.tutorial.cone.ConeBeamBackprojector;
import edu.stanford.rsl.tutorial.cone.ConeBeamCosineFilter;
import edu.stanford.rsl.tutorial.filters.HilbertKernel;
import edu.stanford.rsl.tutorial.filters.RamLakKernel;
import edu.stanford.rsl.tutorial.filters.SheppLoganKernel;

public class ConeBeamReconstructionCustom {

    public static void main(String[] args) {
        new ImageJ();
        
        Configuration.loadConfiguration();
        Configuration conf = Configuration.getGlobalConfiguration();

        Trajectory geo = conf.getGeometry();
        double focalLength = geo.getSourceToDetectorDistance();
        int maxU_PX = geo.getDetectorWidth();
        int maxV_PX = geo.getDetectorHeight();
        double deltaU = geo.getPixelDimensionX();
        double deltaV = geo.getPixelDimensionY();
        double maxU = (maxU_PX) * deltaU;
        double maxV = (maxV_PX) * deltaV;

        // 1. Load Custom TIF Stack
        String filePath = "/home/ankan/Documents/work/outputs/projections/horizontal_big_proj.tif"; 
        ImagePlus imp = IJ.openImage(filePath);
        if (imp == null) {
            IJ.error("Could not open TIF stack at: " + filePath);
            return;
        }
        imp.show("Input TIF Projections");

        Grid3D sino = ImageUtil.wrapImagePlus(imp);
        IJ.log("Loaded stack dimensions -> Width: " + maxU_PX + ", Height: " + maxV_PX + ", Projections: " + geo.getProjectionStackSize());
//        ConeBeamCosineFilter cbFilter = new ConeBeamCosineFilter(focalLength, maxU, maxV, deltaU, deltaV);
//        
//        // Use a SheppLoganKernel or HannKernel instead of RamLakKernel to suppress high-frequency noise
//        SheppLoganKernel kernel = new SheppLoganKernel(maxU_PX, deltaU); 
//        // Alternatively: HannKernel kernel = new HannKernel(maxU_PX, deltaU);
//
//        for (int i = 0; i < geo.getProjectionStackSize(); ++i) {
//            cbFilter.applyToGrid(sino.getSubGrid(i));
//            for (int j = 0; j < maxV_PX; ++j) {
//                kernel.applyToGrid(sino.getSubGrid(i).getSubGrid(j));
//            }
//        }
//        sino.show("Filtered Sinogram");
        
//         2. Filtering Step (Cosine Filter + Ram-Lak)
        IJ.log("Applying filters...");
//        ConeBeamCosineFilter cbFilter = new ConeBeamCosineFilter(focalLength, maxU, maxV, deltaU, deltaV);
//        RamLakKernel ramK = new RamLakKernel(maxU_PX, deltaU);
//        
//        for (int i = 0; i < geo.getProjectionStackSize(); ++i) {
//            cbFilter.applyToGrid(sino.getSubGrid(i));
//            for (int j = 0; j < maxV_PX; ++j) {
//                ramK.applyToGrid(sino.getSubGrid(i).getSubGrid(j));
//            }
//        }
//        sino.show("Filtered Sinogram");
            
        // 3. Backprojection Step (Using CPU method to bypass OpenCL GPU hardware check)
        IJ.log("Running CPU backprojection...");
        try {
            ConeBeamBackprojector cbbp = new ConeBeamBackprojector();
            
            // NOTE: Calling the non-CL CPU method avoids triggering the OpenCL context device type crash
            Grid3D recImage = cbbp.backprojectPixelDriven(sino);
            
            if (recImage != null) {
                recImage.show("Reconstructed 3D Volume (CPU)");
                IJ.log("Reconstruction completed successfully!");
            } else {
                IJ.error("Reconstruction returned a null image."); // Fixed
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
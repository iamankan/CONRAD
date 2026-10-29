package edu.stanford.rsl.tutorial.cone;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageJ;
import ij.plugin.Duplicator;
import edu.stanford.rsl.conrad.data.numeric.Grid3D;
import edu.stanford.rsl.conrad.geometry.trajectories.Trajectory;
import edu.stanford.rsl.conrad.utils.Configuration;
import edu.stanford.rsl.conrad.utils.ImageUtil;
import edu.stanford.rsl.conrad.physics.photoncounting.PhotonCountingEnergyResolvingDetector;
import edu.stanford.rsl.tutorial.cone.ConeBeamBackprojector;
import edu.stanford.rsl.tutorial.cone.ConeBeamCosineFilter;
import edu.stanford.rsl.tutorial.filters.RamLakKernel;

public class ConeBeamReconstructionPCCT {

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

        // 1. Fetch Detector Properties from your active configuration profile
        PhotonCountingEnergyResolvingDetector detector = (PhotonCountingEnergyResolvingDetector) conf.getDetector();
        int numBins = detector.getNumberofBins(); // Reads '6' from XML file
        double[] fluxProfile = detector.getPhotonFluxPerBin();

        // 2. Load Custom 4D Hyperstack TIF (Horizontal_6pcct.tif)
        String filePath = "/home/ankan/Documents/work/outputs/projections/horizontal_small_pcct.tif"; 
        ImagePlus masterImp = IJ.openImage(filePath);
        if (masterImp == null) {
            IJ.error("Could not open TIF stack at: " + filePath);
            return;
        }

        int projectionsPerEnergy = masterImp.getNSlices(); // Reads projections count
        IJ.log("Spectral Detector Active -> Bins Configured: " + numBins);

        // 3. Process each Energy Channel independently with filters enabled
        for (int energyBin = 1; energyBin <= numBins; energyBin++) {
            IJ.log("\n--- Reconstructing Spectral Channel " + energyBin + " ---");

            // Crop out a single 3D sinogram stack for the specific energy channel
            Duplicator dup = new Duplicator();
            ImagePlus energyStackImp = dup.run(masterImp, 1, masterImp.getNChannels(), 1, projectionsPerEnergy, energyBin, energyBin);
            
            // Optional: Uncomment the line below if you want to visually inspect each channel's sinogram stack before filtering
             energyStackImp.show("Sinogram - Energy Channel " + energyBin);

            Grid3D sino = ImageUtil.wrapImagePlus(energyStackImp);

            // Fetch the photon count weighting factor for normalization
            double ioFlux = (energyBin - 1 < fluxProfile.length) ? fluxProfile[energyBin - 1] : 0.0;
            if (ioFlux <= 0.0) {
                IJ.log("Skipping Bin " + energyBin + " -> Flux value is zero.");
                continue;
            }
            
            IJ.log("Bin " + energyBin + " Flux (I_0 Calibration Weight): " + ioFlux);

            // 4. Filtering Step (Cone-beam cosine pre-weighting + Ram-Lak filter)
            ConeBeamCosineFilter cbFilter = new ConeBeamCosineFilter(focalLength, maxU, maxV, deltaU, deltaV);
            RamLakKernel ramK = new RamLakKernel(maxU_PX, deltaU);
            
            for (int i = 0; i < projectionsPerEnergy; ++i) {
                cbFilter.applyToGrid(sino.getSubGrid(i));
                for (int j = 0; j < maxV_PX; ++j) {
                    ramK.applyToGrid(sino.getSubGrid(i).getSubGrid(j));
                }
            }
                
            // 5. CPU Reconstruction 
            try {
                ConeBeamBackprojector cbbp = new ConeBeamBackprojector();
                Grid3D recImage = cbbp.backprojectPixelDriven(sino);
                
                if (recImage != null) {
                    // Display each filtered energy reconstruction window cleanly
                    recImage.show("Reconstructed Volume - Energy Channel " + energyBin);
                    IJ.log("Spectral Channel " + energyBin + " reconstruction finished successfully!");
                } else {
                    IJ.error("Reconstruction returned null for energy bin " + energyBin);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        IJ.log("\nMulti-Energy Spectral Reconstruction pipeline completed.");
    }
}
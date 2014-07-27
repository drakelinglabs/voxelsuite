package com.vitco.export;

import com.vitco.core.data.Data;
import com.vitco.core.data.container.Voxel;
import com.vitco.export.dataStatic.Kv6Static;
import com.vitco.low.CubeIndexer;
import com.vitco.low.hull.HullManager;
import com.vitco.util.components.progressbar.ProgressDialog;
import com.vitco.util.misc.ByteHelper;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.hash.TIntShortHashMap;
import gnu.trove.map.hash.TShortIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Exporter into *.kv6 (Ace of Spades Game)
 */
public class Kv6Exporter2 extends AbstractExporter {

    // constructor
    public Kv6Exporter2(File exportTo, Data data, ProgressDialog dialog) throws IOException {
        super(exportTo, data, dialog);
    }

    // helper to get direction (lighting bit)
    private byte getvdir(short x, short y, short z, HullManager<String> hullManager) {

        // the radius that we check
        int radius = 3;
        // the offsets (direction)
        int ox = 0, oy = 0, oz = 0;

        // loop over different radii
        for (int xr = -radius; xr <= radius; xr++) {
            for (int yr = -radius; yr <= radius; yr++) {
                for (int zr = -radius; zr <= radius; zr++) {
                    // check that point is in sphere
                    if (xr * xr + yr * yr + zr * zr <= (radius + 1) * (radius + 1)) {
                        // check that voxel position is set
                        if (hullManager.contains(CubeIndexer.getId(x + xr, y + yr, z + zr))) {
                            ox += xr;
                            oy += yr;
                            oz += zr;
                        }
                    }
                }
            }
        }

        // If voxels aren't directional (thin), return the 0 vector (no direction)
        double f = ox * ox + oy * oy + oz * oz;
        if (f < 32*32) {
            return (byte) 255;
        }

        double fx = ox;
        double fy = oy;
        double fz = oz;
        double maxf = -1e32;
        int j = 0;
        // loop over all entries and find the best matching one
        for (int i = 0; i < 255; i++) {
            double[] equivecpEntry = Kv6Static.directions[i];
            f = equivecpEntry[0] * fx + equivecpEntry[2] * fy - equivecpEntry[1] * fz;
            if (f > maxf) {
                maxf = f;
                j = i;
            }
        }
        return (byte) j;
    }

    // write the file
    @Override
    protected boolean writeFile() throws IOException {

        // write magic number
        fileOut.writeIntRev(0x6c78764b);

        // write dimension information
        int[] size = getSize();
        fileOut.writeIntRev(size[0]);
        fileOut.writeIntRev(size[2]);
        fileOut.writeIntRev(size[1]);

        // obtain minimum
        int[] min = getMin();

        // write center
        float[] center = getCenter();
        fileOut.writeFloatRev(center[0] - min[0]);
        fileOut.writeFloatRev(center[2] - min[2]);
        fileOut.writeFloatRev(center[1] - min[1]);

        // fetch all visible voxels
        HullManager<String> hullManager = new HullManager<String>();
        for (Voxel voxel : data.getVisibleLayerVoxel()) {
            hullManager.update(voxel.posId, null);
        }
        TIntHashSet visibleVoxel = hullManager.getVisibleVoxelsIds();
        int voxelCount = visibleVoxel.size();
        short[][] voxels = new short[visibleVoxel.size()][];
        TIntIterator iter = visibleVoxel.iterator();
        int i = 0;
        while (iter.hasNext() && i < voxelCount) {
            voxels[i] = CubeIndexer.getPos(iter.next());
            i++;
        }

        // sort voxels
        Arrays.sort(voxels, new Comparator<short[]>() {
            @Override
            public int compare(short[] o1, short[] o2) {
                int dist = o1[0] - o2[0];
                if (dist != 0) {
                    return dist;
                }
                dist = o2[2] - o1[2];
                if (dist != 0) {
                    return dist;
                }
                return o1[1] - o2[1];
            }
        });

        // write the amount of voxel that have a visible side
        fileOut.writeIntRev(voxelCount);

        setActivity("Exporting to file...", false);

        // write all the voxel data
        for (int i1 = 0; i1 < voxels.length; i1++) {
            short[] voxPos = voxels[i1];
            setProgress((i1/(float)voxels.length)*100);
            Color col = data.searchVoxel(
                    new int[]{voxPos[0], voxPos[1], voxPos[2]}, false
            ).getColor();
            // write color
            fileOut.writeByte((byte) col.getBlue());
            fileOut.writeByte((byte) col.getGreen());
            fileOut.writeByte((byte) col.getRed());
            // write "l" (fill byte)
            fileOut.writeByte((byte) 128);
            // write z pos
            fileOut.writeShortRev((short) (voxPos[1] - min[1]));
            // write visible faces
            int voxPosId = CubeIndexer.getId(voxPos);
            byte visibleFaces = 0;
            for (int j = 0; j < 6; j++) {
                if (hullManager.containsBorder(voxPosId, j)) {
                    int mapTo;
                    switch (j) {
                        case 0:
                            mapTo = 6;
                            break;
                        case 1:
                            mapTo = 7;
                            break;
                        case 2:
                            mapTo = 2;
                            break;
                        case 3:
                            mapTo = 3;
                            break;
                        case 4:
                            mapTo = 5;
                            break;
                        default:
                            mapTo = 4;
                            break;
                    }
                    visibleFaces = ByteHelper.setBit(visibleFaces, mapTo);
                }
            }
            fileOut.writeByte(visibleFaces);
            // write lighting byte
            fileOut.writeByte(getvdir(voxPos[0], voxPos[1], voxPos[2], hullManager));
        }

        // collect the offsets
        TShortIntHashMap xMap = new TShortIntHashMap();
        TIntShortHashMap xyMap = new TIntShortHashMap();
        for (short[] voxel : voxels) {
            short shiftedVal = (short) (voxel[0] - min[0]);
            xMap.put(shiftedVal, xMap.get(shiftedVal)+1);

            int id = CubeIndexer.getId(voxel[0] - min[0], voxel[2] - min[2], 0);
            xyMap.put(id, (short) (xyMap.get(id)+1));
        }

        // write the xoffsets
        for (short x = (short) (size[0] - 1); x >= 0; x--) {
            fileOut.writeIntRev(xMap.get(x));
        }

        // write the zyoffset
        for (int x = 0; x < size[0]; x++) {
            for (int y = size[2] - 1; y >= 0; y--) {
                fileOut.writeShortRev(xyMap.get(CubeIndexer.getId(x, y, 0)));
            }
        }

        // success
        return true;
    }

}
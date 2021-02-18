package hex.tree.isoforextended;

import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import net.bytebuddy.implementation.bind.annotation.IgnoreForBinding;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.PrettyPrint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ExtendedIsolationForestTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForestTest.class);

    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScore() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainError() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = -2;
            p._sample_size = -1;
            p._extension_level = - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigData() {
        testBasicBigDataPrivate();
//        testBasicBigDataPrivate();
//        testBasicBigDataPrivate();
    }
    
    private void testBasicBigDataPrivate() {
        ExtendedIsolationForestModel model = null;
        try {
            int P = 128;
            int V = 75_000;
            Scope.enter();
            Frame train = Scope.track(generate_real_only(P, 100_000, 0, 0xCAFFE));
            
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = V;
            p._extension_level = train.numCols() - 1;
            
            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            model = eif.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);
            LOG.info("DKV mem: " + PrettyPrint.bytes(DKV.get(model._key)._max));
            LOG.info("Actual model size: " + PrettyPrint.bytes(convertToBytes(model).length));
            LOG.info("Actual free mem  : " + PrettyPrint.bytes(H2O.CLOUD.free_mem()));
            LOG.info("sample  : " + PrettyPrint.bytes(V*P* Double.BYTES));

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
            if (model != null)
                DKV.remove(model._key);
        }
    }
    
    @Test
    @Ignore
    public void testBasicBigDataIF() {
        try {
            int P = 256;
            int V = 100_000;
            Scope.enter();
            Frame train = Scope.track(generate_real_only(P, 500_000, 0, 0xCAFFE));
            
            IsolationForestModel.IsolationForestParameters p =
                    new IsolationForestModel.IsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = V;
            p._max_depth = (int) Math.ceil(MathUtils.log2(V));
            
            IsolationForest eif = new IsolationForest(p);
            IsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            LOG.info("Actual model size: " + PrettyPrint.bytes(convertToBytes(model).length));
            LOG.info("Actual free mem  : " + PrettyPrint.bytes(H2O.CLOUD.free_mem()));
            LOG.info("sample  : " + PrettyPrint.bytes(V*P* Double.BYTES));
//
//            Frame out = model.score(train);
//            Scope.track_generic(out);
//            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
//            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }    

    @Test
    @Ignore("Expensive")
    public void testBasicBigDataRows() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(2, 65536, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigDataCols() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(128, 500, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicWithCategoricalData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EE", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
        } finally {
            Scope.exit();
        }
    }

    /**
     * String data will be ignored
     */
    @Test
    public void testBasicWithStringData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_STR, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EEa", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void avgPathLengTest() {
        assertEquals(10.244770920116851,
                CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(256), 1e-5);
        assertEquals(11.583643521303037,
                CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(500), 1e-5);
        assertEquals(1, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(2), 1e-5);
        assertEquals(0, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(1), 1e-5);
        assertEquals(0, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(0), 1e-5);
        assertEquals(0, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(-1), 1e-5);
    }

    @Test
    public void testFilterLtTask() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(1.0, 1.0))
                    .withDataForCol(1, ard(1.0, 1.0))
                    .withDataForCol(2, ard(1.0, 1.0))
                    .withDataForCol(3, ard(1.0, 1.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, -1), Vec.newKey());
            Scope.track(v);

            Frame res = new FilterLtTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertEquals("Not correctly filtered",1, res.numRows());
            assertEquals("Column number is wrong",4, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFilterLtTaskCategoricalData() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, 2.0, 0.0, -1.2, 5.5, -5.5, 6.5, 5.5, -5.5, 6.5), Vec.newKey());
            Scope.track(v);

            Frame res = new FilterLtTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertTrue("Column is not categorical", res.vec(1).isCategorical());;
            assertEquals("Not correctly filtered", 3, res.numRows());
            assertEquals("Column number is wrong",3, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFilterGteTask() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(1.0, 1.0))
                    .withDataForCol(1, ard(1.0, 1.0))
                    .withDataForCol(2, ard(1.0, 1.0))
                    .withDataForCol(3, ard(1.0, 1.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, -1.0), Vec.newKey());
            Scope.track(v);

            Frame res = new FilterGteTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertEquals("Not correctly filtered",1, res.numRows());
            assertEquals("Column number is wrong",4, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFilterGteTaskCategoricalData() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, 2.0, 0.0, -1.2, 5.5, -5.5, 6.5, 5.5, -5.5, -7.5), Vec.newKey());
            Scope.track(v);
            Frame res = new FilterGteTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertTrue("Column is not categorical", res.vec(1).isCategorical());;
            assertEquals("Not correctly filtered",6, res.numRows());
            assertEquals("Column number is wrong",3, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeSmoke() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));

            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(9, 1);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();

            long time = end - start;
            if (time > 200) {
                LOG.info("Tree building took a longer than it should.");
            }

            double pathLength = compressedIsolationTree.computePathLength(new double[]{0.0, 0.0}); // Normal Point
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 4);

            pathLength = compressedIsolationTree.computePathLength(new double[]{5.0, 5.0}); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 4);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(32, 32768, 0, 0xBEEF));
            double[] normalPoint = toNumericRow(train, 0);

            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(16, 127);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();

            long time = end - start;
            if (time > 1000) {
                LOG.info("Tree building took a longer than it should: " + time + "ms.");
            }

            double pathLength = compressedIsolationTree.computePathLength(normalPoint);
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 8);

            double[] anomaly = new double[32];
            Arrays.fill(anomaly, 10000.0);
            pathLength = compressedIsolationTree.computePathLength(anomaly); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 8);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testExtendedIsolationTreeSplit() {
        double[][] data = new double[][]{{2.0, 1.0, -1.0}, {5.0, 6.0, -6.0}, {6.0, 0.0, -8.0}};
        double[] p = new double[]{1.0, 4.0, -1.0};
        double[] n = new double[]{-0.25, 0.0, 0.25};

        IsolationTree.FilteredData split = IsolationTree.extendedIsolationForestSplit(data, p, n);

        // Result of (data - p) * n
        // assertArrayEquals("Result is not correct", new double[]{1.5, 0.25, -1.25}, ret.getRes(), 1e-3);

        assertArrayEquals("Result is not correct", new double[]{-1.0}, split.getLeft()[0], 1e-3);
        assertArrayEquals("Result is not correct", new double[]{2.0, 1.0}, split.getRight()[0], 1e-3);
    }

    @Test
    public void testSubSampleTaskSmoke() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            int tries = 100;
            long sum = 0;
            for(int i = 0; i < tries; i++) {
                Frame subSample = SamplingUtils.sampleOfApproxSize(train, 256, 0xBEEF + i);
                assertEquals("SubSample has different number of columns", train.numCols(), subSample.numCols());
                sum += subSample.numRows();
            }
            double average = ((double) sum) / tries;
            assertEquals("SubSample has different number of rows", 256, average, 2);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testSubSampleTaskLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(32, 32768, 0, 0xBEEF));
            int tries = 100;
            long sum = 0;
            for(int i = 0; i < tries; i++) {
                Frame subSample = SamplingUtils.sampleOfApproxSize(train, 256, 0xBEEF + i);
                assertEquals("SubSample has different number of columns", train.numCols(), subSample.numCols());
                sum += subSample.numRows();
            }
            double average = ((double) sum) / tries;
            assertEquals("SubSample has different number of rows", 256, average, 2);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testSubSampleFixedSizeSmoke() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            Frame subSample = SamplingUtils.sampleOfFixedSize(train, 256, 0xBEEF);
            assertEquals("SubSample has different number of columns", train.numCols(), subSample.numCols());
            assertEquals("SubSample has different number of rows", 256, subSample.numRows());
        }
        finally {
            Scope.exit();
        }
    }

    @Test
    public void testSubSampleFixedSizeLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(32, 32768, 0, 0xBEEF));
            Frame subSample = SamplingUtils.sampleOfFixedSize(train, 256, 0xBEEF);
            assertEquals("SubSample has different number of columns", train.numCols(), subSample.numCols());
            assertEquals("SubSample has different number of rows", 256, subSample.numRows());
        }
        finally {
            Scope.exit();
        }
    }
    
    @Test
    public void isolationTreeSizeEstimation() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
        
            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(9, 1);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();
            isolationTree.nodesTotalSize();
            LOG.info("Isolation Tree size: " + PrettyPrint.bytes(convertToBytes(isolationTree).length));
        
            long time = end - start;
            if (time > 200) {
                LOG.info("Tree building took a longer than it should.");
            }
        
            double pathLength = compressedIsolationTree.computePathLength(new double[]{0.0, 0.0}); // Normal Point
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 4);
        
            pathLength = compressedIsolationTree.computePathLength(new double[]{5.0, 5.0}); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 4);
        } finally {
            Scope.exit();
        }
    }
    
    
    private byte[] convertToBytes(Object object){
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
}

package org.nnsuite.nnstreamer;

import android.support.test.runner.AndroidJUnit4;
import android.view.Surface;
import android.view.SurfaceView;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Testcases for Pipeline.
 */
@RunWith(AndroidJUnit4.class)
public class APITestPipeline {
    private int mReceived = 0;
    private boolean mInvalidState = false;
    private Pipeline.State mPipelineState = Pipeline.State.NULL;

    private Pipeline.NewDataCallback mSinkCb = new Pipeline.NewDataCallback() {
        @Override
        public void onNewDataReceived(TensorsData data) {
            if (data == null ||
                data.getTensorsCount() != 1 ||
                data.getTensorData(0).capacity() != 200) {
                mInvalidState = true;
                return;
            }

            TensorsInfo info = data.getTensorsInfo();

            /* validate received data (uint8 2:10:10:1) */
            if (info == null ||
                info.getTensorsCount() != 1 ||
                info.getTensorName(0) != null ||
                info.getTensorType(0) != NNStreamer.TensorType.UINT8 ||
                !Arrays.equals(info.getTensorDimension(0), new int[]{2,10,10,1})) {
                /* received data is invalid */
                mInvalidState = true;
            }

            mReceived++;
        }
    };

    @Before
    public void setUp() {
        APITestCommon.initNNStreamer();

        mReceived = 0;
        mInvalidState = false;
        mPipelineState = Pipeline.State.NULL;
    }

    @Test
    public void enumPipelineState() {
        assertEquals(Pipeline.State.UNKNOWN, Pipeline.State.valueOf("UNKNOWN"));
        assertEquals(Pipeline.State.NULL, Pipeline.State.valueOf("NULL"));
        assertEquals(Pipeline.State.READY, Pipeline.State.valueOf("READY"));
        assertEquals(Pipeline.State.PAUSED, Pipeline.State.valueOf("PAUSED"));
        assertEquals(Pipeline.State.PLAYING, Pipeline.State.valueOf("PLAYING"));
    }

    @Test
    public void testAvailableElement() {
        try {
            assertTrue(Pipeline.isElementAvailable("tensor_converter"));
            assertTrue(Pipeline.isElementAvailable("tensor_filter"));
            assertTrue(Pipeline.isElementAvailable("tensor_transform"));
            assertTrue(Pipeline.isElementAvailable("tensor_sink"));
            assertTrue(Pipeline.isElementAvailable("join"));
            assertTrue(Pipeline.isElementAvailable("amcsrc"));
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testAvailableElementNullName_n() {
        try {
            Pipeline.isElementAvailable(null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testAvailableElementEmptyName_n() {
        try {
            Pipeline.isElementAvailable("");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testAvailableElementInvalidName_n() {
        try {
            assertFalse(Pipeline.isElementAvailable("invalid-element"));
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testConstructInvalidElement_n() {
        String desc = "videotestsrc ! videoconvert ! video/x-raw,format=RGB ! " +
                "invalidelement ! tensor_converter ! tensor_sink";

        try {
            new Pipeline(desc);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testConstructNullDescription_n() {
        try {
            new Pipeline(null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testConstructEmptyDescription_n() {
        try {
            new Pipeline("");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testConstructNullStateCb() {
        String desc = "videotestsrc ! videoconvert ! video/x-raw,format=RGB ! " +
                "tensor_converter ! tensor_sink";

        try (Pipeline pipe = new Pipeline(desc, null)) {
            Thread.sleep(100);
            assertEquals(Pipeline.State.PAUSED, pipe.getState());
            Thread.sleep(100);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testConstructWithStateCb() {
        String desc = "videotestsrc ! videoconvert ! video/x-raw,format=RGB ! " +
                "tensor_converter ! tensor_sink";

        /* pipeline state callback */
        Pipeline.StateChangeCallback stateCb = new Pipeline.StateChangeCallback() {
            @Override
            public void onStateChanged(Pipeline.State state) {
                mPipelineState = state;
            }
        };

        try (Pipeline pipe = new Pipeline(desc, stateCb)) {
            Thread.sleep(100);
            assertEquals(Pipeline.State.PAUSED, mPipelineState);

            /* start pipeline */
            pipe.start();
            Thread.sleep(300);

            assertEquals(Pipeline.State.PLAYING, mPipelineState);

            /* stop pipeline */
            pipe.stop();
            Thread.sleep(300);

            assertEquals(Pipeline.State.PAUSED, mPipelineState);
            Thread.sleep(100);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testGetState() {
        String desc = "videotestsrc ! videoconvert ! video/x-raw,format=RGB ! " +
                "tensor_converter ! tensor_sink";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();
            Thread.sleep(300);

            assertEquals(Pipeline.State.PLAYING, pipe.getState());

            /* stop pipeline */
            pipe.stop();
            Thread.sleep(300);

            assertEquals(Pipeline.State.PAUSED, pipe.getState());
            Thread.sleep(100);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testRegisterNullDataCb_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.registerSinkCallback("sinkx", null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testRegisterDataCbInvalidName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.registerSinkCallback("invalid_sink", mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testRegisterDataCbNullName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.registerSinkCallback(null, mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testRegisterDataCbEmptyName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.registerSinkCallback("", mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testUnregisterNullDataCb_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.unregisterSinkCallback("sinkx", null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testUnregisterDataCbNullName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.unregisterSinkCallback(null, mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testUnregisterDataCbEmptyName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.unregisterSinkCallback("", mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testUnregisteredDataCb_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.unregisterSinkCallback("sinkx", mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testUnregisterInvalidCb_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register callback */
            Pipeline.NewDataCallback cb1 = new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    mReceived++;
                }
            };

            pipe.registerSinkCallback("sinkx", cb1);

            /* unregistered callback */
            pipe.unregisterSinkCallback("sinkx", mSinkCb);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testRemoveDataCb() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", mSinkCb);

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);
            }

            /* pause pipeline and unregister sink callback */
            Thread.sleep(100);
            pipe.stop();

            pipe.unregisterSinkCallback("sinkx", mSinkCb);
            Thread.sleep(100);

            /* start pipeline again */
            pipe.start();

            /* push input buffer again */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);
            }

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testDuplicatedDataCb() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            pipe.registerSinkCallback("sinkx", mSinkCb);

            /* try to register same cb */
            pipe.registerSinkCallback("sinkx", mSinkCb);

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);
            }

            /* pause pipeline and unregister sink callback */
            Thread.sleep(100);
            pipe.stop();

            pipe.unregisterSinkCallback("sinkx", mSinkCb);
            Thread.sleep(100);

            /* start pipeline again */
            pipe.start();

            /* push input buffer again */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);
            }

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testMultipleDataCb() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* register three callbacks */
            Pipeline.NewDataCallback cb1 = new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    mReceived++;
                }
            };

            Pipeline.NewDataCallback cb2 = new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    mReceived++;
                }
            };

            pipe.registerSinkCallback("sinkx", mSinkCb);
            pipe.registerSinkCallback("sinkx", cb1);
            pipe.registerSinkCallback("sinkx", cb2);

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);
            }

            /* pause pipeline and unregister sink callback */
            Thread.sleep(100);
            pipe.stop();

            pipe.unregisterSinkCallback("sinkx", mSinkCb);
            pipe.unregisterSinkCallback("sinkx", cb1);
            Thread.sleep(100);

            /* start pipeline again */
            pipe.start();

            /* push input buffer again */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);
            }

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(40, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testPushToTensorTransform() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)5:1:1:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_transform mode=arithmetic option=typecast:float32,add:0.5 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{5,1,1,1});

            /* register callback */
            Pipeline.NewDataCallback cb1 = new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data != null) {
                        TensorsInfo info = data.getTensorsInfo();
                        ByteBuffer buffer = data.getTensorData(0);

                        /* validate received data (float32 5:1:1:1) */
                        if (info == null ||
                            info.getTensorsCount() != 1 ||
                            info.getTensorType(0) != NNStreamer.TensorType.FLOAT32 ||
                            !Arrays.equals(info.getTensorDimension(0), new int[]{5,1,1,1})) {
                            /* received data is invalid */
                            mInvalidState = true;
                        }

                        for (int i = 0; i < 5; i++) {
                            float expected = i * 2 + mReceived + 0.5f;

                            if (expected != buffer.getFloat(i * 4)) {
                                mInvalidState = true;
                            }
                        }

                        mReceived++;
                    }
                }
            };

            pipe.registerSinkCallback("sinkx", cb1);

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                TensorsData in = info.allocate();
                ByteBuffer buffer = in.getTensorData(0);

                for (int j = 0; j < 5; j++) {
                    buffer.put(j, (byte) (j * 2 + i));
                }

                in.setTensorData(0, buffer);

                pipe.inputData("srcx", in);
                Thread.sleep(50);
            }

            /* pause pipeline and unregister sink callback */
            Thread.sleep(200);
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testRunModel() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.TENSORFLOW_LITE)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getTFLiteImgModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=tensorflow-lite model=" + model.getAbsolutePath() + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{3,224,224,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    TensorsInfo info = data.getTensorsInfo();

                    if (info == null || info.getTensorsCount() != 1) {
                        mInvalidState = true;
                    } else {
                        ByteBuffer output = data.getTensorData(0);

                        if (!APITestCommon.isValidBuffer(output, 1001)) {
                            mInvalidState = true;
                        }
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(100);
            }

            /* sleep 500 to invoke */
            Thread.sleep(500);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testClassificationResult() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.TENSORFLOW_LITE)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getTFLiteImgModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=tensorflow-lite model=" + model.getAbsolutePath() + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    int labelIndex = APITestCommon.getMaxScore(buffer);

                    /* check label index (orange) */
                    if (labelIndex != 951) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            TensorsData in = APITestCommon.readRawImageData();
            pipe.inputData("srcx", in);

            /* sleep 1000 to invoke */
            Thread.sleep(1000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testInputBuffer() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", mSinkCb);

            /* start pipeline */
            pipe.start();

            /* push input buffer repeatedly */
            for (int i = 0; i < 2048; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(20);
            }

            /* sleep 300 to pass input buffers to sink */
            Thread.sleep(300);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testInputVideo() {
        String desc = "appsrc name=srcx ! " +
                "video/x-raw,format=RGB,width=320,height=240,framerate=(fraction)0/1 ! " +
                "tensor_converter ! tensor_sink name=sinkx";

        /* For media format, set meta with exact buffer size. */
        TensorsInfo info = new TensorsInfo();
        /* input data : RGB 320x240 */
        info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{3 * 320 * 240});

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    /* check received data */
                    TensorsInfo info = data.getTensorsInfo();
                    NNStreamer.TensorType type = info.getTensorType(0);
                    int[] dimension = info.getTensorDimension(0);

                    if (type != NNStreamer.TensorType.UINT8) {
                        mInvalidState = true;
                    }

                    if (dimension[0] != 3 || dimension[1] != 320 ||
                        dimension[2] != 240 || dimension[3] != 1) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(30);
            }

            /* sleep 200 to invoke */
            Thread.sleep(200);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testInputAudio() {
        String desc = "appsrc name=srcx ! " +
                "audio/x-raw,format=S16LE,rate=16000,channels=1 ! " +
                "tensor_converter frames-per-tensor=500 ! tensor_sink name=sinkx";

        /* For media format, set meta with exact buffer size. */
        TensorsInfo info = new TensorsInfo();
        /* input data : 16k sample rate, mono, signed 16bit little-endian, 500 samples */
        info.addTensorInfo(NNStreamer.TensorType.INT16, new int[]{500});

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    /* check received data */
                    TensorsInfo info = data.getTensorsInfo();
                    NNStreamer.TensorType type = info.getTensorType(0);
                    int[] dimension = info.getTensorDimension(0);

                    if (type != NNStreamer.TensorType.INT16) {
                        mInvalidState = true;
                    }

                    if (dimension[0] != 1 || dimension[1] != 500) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(30);
            }

            /* sleep 200 to invoke */
            Thread.sleep(200);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testInputInvalidName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* start pipeline */
            pipe.start();

            pipe.inputData("invalid_src", TensorsData.allocate(info));
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testInputNullName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* start pipeline */
            pipe.start();

            pipe.inputData(null, TensorsData.allocate(info));
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testInputEmptyName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* start pipeline */
            pipe.start();

            pipe.inputData("", TensorsData.allocate(info));
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testInputNullData_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            pipe.inputData("srcx", null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testInputInvalidData_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{4,10,10,2});

            TensorsData in = TensorsData.allocate(info);

            /* push data with invalid size */
            pipe.inputData("srcx", in);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSelectSwitch() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", mSinkCb);

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(50);

                if (i == 9) {
                    /* select pad */
                    pipe.selectSwitchPad("outs", "src_1");
                }
            }

            /* sleep 300 to pass all input buffers to sink */
            Thread.sleep(300);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testGetSwitchPad() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* get pad list of output-selector */
            String[] pads = pipe.getSwitchPads("outs");

            assertEquals(2, pads.length);
            assertEquals("src_0", pads[0]);
            assertEquals("src_1", pads[1]);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testGetSwitchInvalidName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* get pad list with invalid switch name */
            pipe.getSwitchPads("invalid_outs");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testGetSwitchNullName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* get pad list with null param */
            pipe.getSwitchPads(null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testGetSwitchEmptyName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* get pad list with empty name */
            pipe.getSwitchPads("");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSelectInvalidPad_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* select invalid pad name */
            pipe.selectSwitchPad("outs", "invalid_src");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSelectNullPad_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* null pad name */
            pipe.selectSwitchPad("outs", null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSelectEmptyPad_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* empty pad name */
            pipe.selectSwitchPad("outs", "");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSelectNullSwitchName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* null switch name */
            pipe.selectSwitchPad(null, "src_1");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSelectEmptySwitchName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "output-selector name=outs " +
                "outs.src_0 ! tensor_sink name=sinkx async=false " +
                "outs.src_1 ! tensor_sink async=false";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* empty switch name */
            pipe.selectSwitchPad("", "src_1");
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testControlValve() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tee name=t " +
                "t. ! queue ! tensor_sink " +
                "t. ! queue ! valve name=valvex ! tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.UINT8, new int[]{2,10,10,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", mSinkCb);

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                pipe.inputData("srcx", info.allocate());
                Thread.sleep(50);

                if (i == 9) {
                    /* close valve */
                    pipe.controlValve("valvex", false);
                }
            }

            /* sleep 300 to pass all input buffers to sink */
            Thread.sleep(300);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testControlInvalidValve_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tee name=t " +
                "t. ! queue ! tensor_sink " +
                "t. ! queue ! valve name=valvex ! tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* control valve with invalid name */
            pipe.controlValve("invalid_valve", false);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testControlNullValveName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tee name=t " +
                "t. ! queue ! tensor_sink " +
                "t. ! queue ! valve name=valvex ! tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* control valve with null name */
            pipe.controlValve(null, false);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testControlEmptyValveName_n() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)2:10:10:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tee name=t " +
                "t. ! queue ! tensor_sink " +
                "t. ! queue ! valve name=valvex ! tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* start pipeline */
            pipe.start();

            /* control valve with empty name */
            pipe.controlValve("", false);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSetNullSurface() {
        if (!Pipeline.isElementAvailable("glimagesink")) {
            /* cannot run the test */
            return;
        }

        String desc = "videotestsrc ! videoconvert ! glimagesink name=vsink";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.start();
            Thread.sleep(500);

            /* Setting null surface will release old window */
            pipe.setSurface("vsink", null);

            Thread.sleep(500);
            pipe.stop();
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSetSurfaceNullName_n() {
        if (!Pipeline.isElementAvailable("glimagesink")) {
            /* cannot run the test */
            return;
        }

        String desc = "videotestsrc ! videoconvert ! glimagesink name=vsink";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.start();
            Thread.sleep(500);

            pipe.setSurface(null, null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSetSurfaceEmptyName_n() {
        if (!Pipeline.isElementAvailable("glimagesink")) {
            /* cannot run the test */
            return;
        }

        String desc = "videotestsrc ! videoconvert ! glimagesink name=vsink";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.start();
            Thread.sleep(500);

            pipe.setSurface("", null);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testSetInvalidSurface_n() {
        if (!Pipeline.isElementAvailable("glimagesink")) {
            /* cannot run the test */
            return;
        }

        /* invalid surface */
        SurfaceView surfaceView = new SurfaceView(APITestCommon.getContext());
        Surface surface = surfaceView.getHolder().getSurface();

        if (surface.isValid()) {
            return;
        }

        String desc = "videotestsrc ! videoconvert ! glimagesink name=vsink";

        try (Pipeline pipe = new Pipeline(desc)) {
            pipe.start();
            Thread.sleep(500);

            pipe.setSurface("vsink", surface);
            fail();
        } catch (Exception e) {
            /* expected */
        }
    }

    @Test
    public void testAMCsrc() {
        String media = APITestCommon.getTestVideoPath();

        String desc = "amcsrc location=" + media + " ! " +
                "videoconvert ! videoscale ! video/x-raw,format=RGB,width=320,height=240 ! " +
                "tensor_converter ! tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    TensorsInfo info = data.getTensorsInfo();

                    if (info == null || info.getTensorsCount() != 1) {
                        mInvalidState = true;
                    } else {
                        ByteBuffer output = data.getTensorData(0);

                        if (!APITestCommon.isValidBuffer(output, 230400)) {
                            mInvalidState = true;
                        }
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* sleep 2 seconds to invoke */
            Thread.sleep(2000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);

            /* sleep 1 second and restart */
            Thread.sleep(1000);
            mReceived = 0;

            pipe.start();

            /* sleep 2 seconds to invoke */
            Thread.sleep(2000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    /**
     * Run SNAP with Caffe model.
     */
    private void runSNAPCaffe(APITestCommon.SNAPComputingUnit computingUnit) {
        File[] models = APITestCommon.getSNAPCaffeModel();
        String option = APITestCommon.getSNAPCaffeOption(computingUnit);

        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=snap " +
                    "model=" + models[0].getAbsolutePath() + "," + models[1].getAbsolutePath() + " " +
                    "input=3:224:224:1 inputtype=float32 inputlayout=NHWC inputname=data " +
                    "output=1:1:1000:1 outputtype=float32 outputlayout=NCHW outputname=prob " +
                    "custom=" + option + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.FLOAT32, new int[]{3,224,224,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    TensorsInfo info = data.getTensorsInfo();

                    if (info == null || info.getTensorsCount() != 1) {
                        mInvalidState = true;
                    } else {
                        ByteBuffer output = data.getTensorData(0);

                        if (!APITestCommon.isValidBuffer(output, 4000)) {
                            mInvalidState = true;
                        }
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(100);
            }

            /* sleep 500 to invoke */
            Thread.sleep(500);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSNAPCaffeCPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        runSNAPCaffe(APITestCommon.SNAPComputingUnit.CPU);
    }

    @Test
    public void testSNAPCaffeGPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        runSNAPCaffe(APITestCommon.SNAPComputingUnit.GPU);
    }

    /**
     * Run SNAP with Tensorflow model.
     */
    private void runSNAPTensorflow(APITestCommon.SNAPComputingUnit computingUnit) {
        File[] model = APITestCommon.getSNAPTensorflowModel(computingUnit);
        String option = APITestCommon.getSNAPTensorflowOption(computingUnit);
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=snap " +
                    "model=" + model[0].getAbsolutePath() + " " +
                    "input=3:224:224:1 inputtype=float32 inputlayout=NHWC inputname=input " +
                    "output=1001:1 outputtype=float32 outputlayout=NHWC outputname=MobilenetV1/Predictions/Reshape_1:0 " +
                    "custom=" + option + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.FLOAT32, new int[]{3,224,224,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    TensorsInfo info = data.getTensorsInfo();

                    if (info == null || info.getTensorsCount() != 1) {
                        mInvalidState = true;
                    } else {
                        ByteBuffer output = data.getTensorData(0);

                        if (!APITestCommon.isValidBuffer(output, 4004)) {
                            mInvalidState = true;
                        }
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(100);
            }

            /* sleep 500 to invoke */
            Thread.sleep(500);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSNAPTensorflowCPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        runSNAPTensorflow(APITestCommon.SNAPComputingUnit.CPU);
    }

    @Test
    public void testSNAPTensorflowDSP() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        if (!android.os.Build.HARDWARE.equals("qcom")) {
            /*
             * Tensorflow model using DSP runtime can only be executed on
             * Snapdragon SoC. Cannot run this test on exynos.
             */
            return;
        }

        runSNAPTensorflow(APITestCommon.SNAPComputingUnit.DSP);
    }

    @Test
    public void testSNAPTensorflowNPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        if (!android.os.Build.HARDWARE.equals("qcom")) {
            /*
             * Tensorflow model using NPU runtime can only be executed on
             * Snapdragon. Cannot run this test on exynos.
             */
            return;
        }

        runSNAPTensorflow(APITestCommon.SNAPComputingUnit.NPU);
    }

    /**
     * Run SNAP with Tensorflow-Lite model (mobilenet_v1_1.0_224.tflite).
     */
    private void runSNAPTensorflowLite(APITestCommon.SNAPComputingUnit computingUnit) {
        File[] model = APITestCommon.getSNAPTensorflowLiteModel();
        String option = APITestCommon.getSNAPTensorflowLiteOption(computingUnit);
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_transform mode=arithmetic option=typecast:float32,add:-127.5,div:127.5 ! " +
                "tensor_filter framework=snap " +
                "model=" + model[0].getAbsolutePath() + " " +
                "input=3:224:224:1 inputtype=float32 inputlayout=NHWC inputname=input " +
                "output=1001:1 outputtype=float32 outputlayout=NHWC outputname=MobilenetV1/Predictions/Reshape_1 " +
                "custom=" + option + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    int labelIndex = APITestCommon.getMaxScoreFloatBuffer(buffer, 1001);

                    /* check label index (orange) */
                    if (labelIndex != 951) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                TensorsData in = APITestCommon.readRawImageData();
                pipe.inputData("srcx", in);
                Thread.sleep(100);
            }

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSNAPTensorflowLiteCPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        runSNAPTensorflowLite(APITestCommon.SNAPComputingUnit.CPU);
    }

    @Test
    public void testSNAPTensorflowLiteGPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        runSNAPTensorflowLite(APITestCommon.SNAPComputingUnit.GPU);
    }

    @Test
    public void testSNAPTensorflowLiteNPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNAP)) {
            /* cannot run the test */
            return;
        }

        if (android.os.Build.HARDWARE.equals("qcom")) {
            /*
             * TensorflowLite model using NPU runtime can only be executed on
             * Exynos. Cannot run this test on Snapdragon.
             */
            return;
        }

        runSNAPTensorflowLite(APITestCommon.SNAPComputingUnit.NPU);
    }


    @Test
    public void testNNFWTFLite() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.NNFW)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getTFLiteAddModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)1:1:1:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=nnfw model=" + model.getAbsolutePath() + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.FLOAT32, new int[]{1,1,1,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    float expected = buffer.getFloat(0);

                    /* check received data */
                    if (expected != 3.5f) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            TensorsData input = info.allocate();

            ByteBuffer buffer = input.getTensorData(0);
            buffer.putFloat(0, 1.5f);

            input.setTensorData(0, buffer);

            pipe.inputData("srcx", input);

            /* sleep 1000 to invoke */
            Thread.sleep(1000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSNPE() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getSNPEModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:299:299:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=snpe " + "model=" + model.getAbsolutePath() + " ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.FLOAT32, new int[]{3,299,299,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    TensorsInfo info = data.getTensorsInfo();

                    if (info == null || info.getTensorsCount() != 1) {
                        mInvalidState = true;
                    } else {
                        ByteBuffer output = data.getTensorData(0);

                        if (!APITestCommon.isValidBuffer(output, 4004)) {
                            mInvalidState = true;
                        }
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(100);
            }

            /* sleep 500 to invoke */
            Thread.sleep(500);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    private void runSNPEMultipleOutput(String desc) {
        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.FLOAT32, new int[]{3,300,300,1});

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 2) {
                        mInvalidState = true;
                        return;
                    }

                    TensorsInfo info = data.getTensorsInfo();

                    if (info == null || info.getTensorsCount() != 2) {
                        mInvalidState = true;
                    } else {
                        ByteBuffer output = data.getTensorData(0);
                        if (!APITestCommon.isValidBuffer(output, 1917 * 4 * 4)) {
                            mInvalidState = true;
                        }

                        output = data.getTensorData(1);
                        if (!APITestCommon.isValidBuffer(output, 1917 * 91 * 4)) {
                            mInvalidState = true;
                        }
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 10; i++) {
                /* dummy input */
                pipe.inputData("srcx", TensorsData.allocate(info));
                Thread.sleep(100);
            }

            /* sleep 1000ms to invoke */
            Thread.sleep(1000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }

    }

    @Test
    public void testSNPEMultipleOutputWithTensorInfo() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getMultiOutputSNPEModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:300:300:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=snpe " + "model=" + model.getAbsolutePath() +
                " output=4:1:1917:1,91:1917:1:1 outputtype=float32,float32 outputname=concat:0,concat_1:0 ! " +
                "tensor_sink name=sinkx";

        runSNPEMultipleOutput(desc);
    }

    @Test
    public void testSNPEMultipleOutputWithCustomProp() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getMultiOutputSNPEModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:300:300:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=snpe " + "model=" + model.getAbsolutePath() +
                " custom=UserBuffer:true,OutputTensor:concat:0;concat_1:0 ! " +
                "tensor_sink name=sinkx";

        runSNPEMultipleOutput(desc);
    }

    /**
     * Run SNPE with inception model with given runtime.
     */
    private void runSNPEInception(String runtime) {
        File model = APITestCommon.getSNPEModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:299:299:1,type=(string)float32,framerate=(fraction)0/1 ! " +
                "tensor_filter framework=snpe model=" + model.getAbsolutePath() +
                " custom=Runtime:" + runtime + " ! " +
                "tensor_sink name=sinkx";

        /* expected label is measuring_cup (648) */
        final int expected_label = 648;

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    int labelIndex = APITestCommon.getMaxScoreFloatBuffer(buffer, 1001);

                    /* check label index (measuring cup) */
                    if (labelIndex != expected_label) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            TensorsData in = APITestCommon.readRawImageDataSNPE();
            pipe.inputData("srcx", in);

            /* sleep 1000 msec to invoke */
            Thread.sleep(1000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSNPEClassificationResultCPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        runSNPEInception("CPU");
    }

    @Test
    public void testSNPEClassificationResultGPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        runSNPEInception("GPU");
    }

    @Test
    public void testSNPEClassificationResultDSP() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        runSNPEInception("DSP");
    }

    @Test
    public void testSNPEClassificationResultNPU() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.SNPE)) {
            /* cannot run the test */
            return;
        }

        runSNPEInception("NPU");
    }

    @Test
    public void testPytorchClassificationResult() {
        if (!NNStreamer.isAvailable(NNStreamer.NNFWType.PYTORCH)) {
            /* cannot run the test */
            return;
        }

        File model = APITestCommon.getPytorchModel();
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_transform mode=dimchg option=0:2 ! " +
                "tensor_transform mode=arithmetic option=typecast:float32,add:-127.5,div:127.5 ! " +
                "tensor_filter framework=pytorch model=" + model.getAbsolutePath() + " " +
                "input=224:224:3:1 inputtype=float32 output=1000:1 outputtype=float32 ! " +
                "tensor_sink name=sinkx";

        /* expected label is orange (950) */
        final int expected_label = 950;

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    int labelIndex = APITestCommon.getMaxScoreFloatBuffer(buffer, 1000);

                    /* check label index (orange) */
                    if (labelIndex != expected_label) {
                        mInvalidState = true;
                    }
                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            TensorsData in = APITestCommon.readRawImageData();
            pipe.inputData("srcx", in);

            /* sleep 1000 to invoke */
            Thread.sleep(1000);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testFlushPipeline() {
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)10:1:1:1,type=(string)int32,framerate=(fraction)0/1 ! " +
                "tensor_aggregator frames-in=10 frames-out=3 frames-flush=3 frames-dim=0 ! " +
                "tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            TensorsInfo info = new TensorsInfo();
            info.addTensorInfo(NNStreamer.TensorType.INT32, new int[]{10,1,1,1});

            TensorsData input = info.allocate();
            ByteBuffer buffer = input.getTensorData(0);

            for (int i = 0; i < 10; i++) {
                buffer.putInt(i * 4, i + 1);
            }

            input.setTensorData(0, buffer);

            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    mReceived++;

                    if (mReceived == 1) {
                        ByteBuffer buffer = data.getTensorData(0);

                        if (buffer.getInt(0) != 1 ||
                            buffer.getInt(4) != 2 ||
                            buffer.getInt(8) != 3) {
                            mInvalidState = true;
                        }
                    }
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer and check received data */
            pipe.inputData("srcx", input);
            Thread.sleep(200);
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);

            /* flush the pipeline and push again */
            pipe.flush(true);
            mReceived = 0;

            pipe.inputData("srcx", input);
            Thread.sleep(200);
            assertFalse(mInvalidState);
            assertTrue(mReceived > 0);

            /* stop pipeline */
            pipe.stop();
        } catch (Exception e) {
            fail();
        }
    }

    @Ignore("Build library with flatbuf.")
    @Test
    public void testFlatbuf() {
        /* This test assume that the NNStreamer library is built with Flatbuf (default option) */
        String desc = "appsrc name=srcx ! " +
                "other/tensor,dimension=(string)3:224:224:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                "tensor_decoder mode=flatbuf ! other/flatbuf-tensor,framerate=(fraction)0/1 ! " +
                "tensor_converter ! tensor_sink name=sinkx";

        try (Pipeline pipe = new Pipeline(desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    ByteBuffer bufferOri = APITestCommon.readRawImageData().getTensorData(0);
                    bufferOri.rewind();

                    /* check with original input data */
                    if (!buffer.equals(bufferOri)) {
                        mInvalidState = true;
                    }

                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            TensorsData in = APITestCommon.readRawImageData();
            pipe.inputData("srcx", in);

            /* sleep 100 to invoke */
            Thread.sleep(100);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(1, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Ignore("Build library with mqtt and check available mqtt broker is ready.")
    @Test
    public void testMQTTElement() {
        String sub_desc = "mqttsrc sub-topic=test/videotestsrc ! " +
                "video/x-raw,format=RGB,width=40,height=40,framerate=5/1 ! " +
                "tensor_converter ! tensor_sink name=sinkx";
        String pub_desc = "videotestsrc is-live=true num-buffers=10 ! " +
                "video/x-raw,format=RGB,width=40,height=40,framerate=5/1 ! " +
                "mqttsink pub-topic=test/videotestsrc";

        try {
            Pipeline sub_pipe = new Pipeline(sub_desc);
            /* register sink callback */
            sub_pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }
                    mReceived++;
                }
            });
            sub_pipe.start();

            Pipeline pub_pipe = new Pipeline(pub_desc);
            pub_pipe.start();

            Thread.sleep(3000);

            sub_pipe.stop();
            pub_pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testAppsrcPng() {
        if (!Pipeline.isElementAvailable("pngdec")) {
            /* cannot run the test */
            return;
        }

        String pipeline_desc = "appsrc name=srcx caps=image/png ! pngdec ! videoconvert ! videoscale ! video/x-raw,format=RGB,width=224,height=224,framerate=0/1 ! " +
                "tensor_converter ! tensor_sink name=sinkx";
        try (Pipeline pipe = new Pipeline(pipeline_desc)) {
            /* register sink callback */
            pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }

                    ByteBuffer buffer = data.getTensorData(0);
                    ByteBuffer bufferOri = APITestCommon.readRawImageData().getTensorData(0);
                    bufferOri.rewind();

                    /* check with original input data */
                    if (!buffer.equals(bufferOri)) {
                       mInvalidState = true;
                    }
                    mReceived++;
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            TensorsData data = APITestCommon.readPngImage();
            pipe.inputData("srcx", data);

            /* sleep 100ms to invoke */
            Thread.sleep(100);

            /* stop pipeline */
            pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(1, mReceived);
        } catch (Exception e) {
            fail();
        }
    }

    @Ignore("Build library with nnstreamer-edge and check available port.")
    @Test
    public void testTensorQuery() {
        int serversrc_port = APITestCommon.getAvailablePort();
        int client_port = APITestCommon.getAvailablePort();
        String server_desc = "tensor_query_serversrc port=" + serversrc_port + " num-buffers=10 ! " +
                "other/tensors,format=static,num_tensors=1,dimensions=3:4:4:1,types=uint8,framerate=0/1 ! " +
                "tensor_query_serversink async=false";
        String client_desc = "videotestsrc is-live=true num-buffers=10 ! " +
                "video/x-raw,format=RGB,width=4,height=4,framerate=5/1 ! " +
                "tensor_converter ! tensor_query_client timeout=1000 dest-port=" + serversrc_port + " port=" + client_port + " ! " +
                "tensor_sink name=sinkx";

        try {
            Pipeline server_pipe = new Pipeline(server_desc);
            server_pipe.start();

            Thread.sleep(1000);

            Pipeline client_pipe = new Pipeline(client_desc);
            /* register sink callback */
            client_pipe.registerSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                @Override
                public void onNewDataReceived(TensorsData data) {
                    if (data == null || data.getTensorsCount() != 1) {
                        mInvalidState = true;
                        return;
                    }
                    mReceived++;
                }
            });
            client_pipe.start();

            Thread.sleep(3000);

            server_pipe.stop();
            client_pipe.stop();

            /* check received data from sink */
            assertFalse(mInvalidState);
            assertEquals(10, mReceived);
        } catch (Exception e) {
            fail();
        }
    }
}

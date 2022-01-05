/**
 * @file        unittest_capi_inference_snpe.cc
 * @date        04 Jan 2022
 * @brief       Unit test for ML inference C-API (SNPE).
 * @see         https://github.com/nnstreamer/nnstreamer
 * @author      Yongjoo Ahn <yongjoo1.ahn@samsung.com>
 * @bug         No known bugs
 */

#include <gtest/gtest.h>
#include <fcntl.h>
#include <glib.h>
#include <glib/gstdio.h> /* GStatBuf */
#include <nnstreamer.h>
#include <nnstreamer-single.h>
#include <nnstreamer_plugin_api.h>
#include <nnstreamer_internal.h>
#include <nnstreamer-tizen-internal.h>
#include <ml-api-internal.h>
#include <ml-api-inference-internal.h>

static const unsigned int SINGLE_DEF_TIMEOUT_MSEC = 10000U;

/**
 * @brief Macro to wait for pipeline state.
 */
#define wait_for_start(handle, state, status) do { \
    int counter = 0; \
    while ((state == ML_PIPELINE_STATE_PAUSED || state == ML_PIPELINE_STATE_READY) \
           && counter < 20) { \
      g_usleep (50000); \
      counter++; \
      status = ml_pipeline_get_state (handle, &state); \
      EXPECT_EQ (status, ML_ERROR_NONE); \
    } \
  } while (0)

#define RUNTIME_STRING_CPU "Runtime:CPU"
#define RUNTIME_STRING_GPU "Runtime:GPU"
#define RUNTIME_STRING_DSP "Runtime:DSP"
#define RUNTIME_STRING_NPU "Runtime:NPU"

/**
 * @brief Test NNStreamer single shot (snpe)
 * model: inception_v3_quantized.dlc
 * with dummy input
 */
TEST (nnstreamer_capi_singleshot, invoke_01)
{
  ml_single_h single;
  ml_tensors_info_h in_info, out_info;
  ml_tensors_info_h in_res, out_res;
  ml_tensors_data_h input, output;
  ml_tensor_dimension in_dim, out_dim, res_dim;
  ml_tensor_type_e type = ML_TENSOR_TYPE_UNKNOWN;
  unsigned int count = 0;
  char *name = NULL;
  int status;

  const gchar *root_path = g_getenv ("NNSTREAMER_SOURCE_ROOT_PATH");
  gchar *test_model;

  /* supposed to run test in build directory */
  if (root_path == NULL)
    root_path = "..";

  test_model = g_build_filename (root_path, "tests", "test_models", "models",
      "inception_v3_quantized.dlc", NULL);
  ASSERT_TRUE (g_file_test (test_model, G_FILE_TEST_EXISTS));

  ml_tensors_info_create (&in_info);
  ml_tensors_info_create (&out_info);

  in_dim[0] = 3;
  in_dim[1] = 299;
  in_dim[2] = 299;
  in_dim[3] = 1;
  ml_tensors_info_set_count (in_info, 1);
  ml_tensors_info_set_tensor_type (in_info, 0, ML_TENSOR_TYPE_FLOAT32);
  ml_tensors_info_set_tensor_dimension (in_info, 0, in_dim);

  out_dim[0] = 1001;
  out_dim[1] = 1;
  out_dim[2] = 1;
  out_dim[3] = 1;
  ml_tensors_info_set_count (out_info, 1);
  ml_tensors_info_set_tensor_type (out_info, 0, ML_TENSOR_TYPE_FLOAT32);
  ml_tensors_info_set_tensor_dimension (out_info, 0, out_dim);

  status = ml_single_open_full (&single, test_model, NULL, NULL,
      ML_NNFW_TYPE_SNPE, ML_NNFW_HW_ANY, RUNTIME_STRING_CPU);

  EXPECT_EQ (status, ML_ERROR_NONE);

  /* input tensor in filter */
  status = ml_single_get_input_info (single, &in_res);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_tensors_info_get_count (in_res, &count);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_EQ (count, 1U);

  status = ml_tensors_info_get_tensor_name (in_res, 0, &name);
  EXPECT_EQ (status, ML_ERROR_NONE);
  g_free (name);

  status = ml_tensors_info_get_tensor_type (in_res, 0, &type);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_EQ (type, ML_TENSOR_TYPE_FLOAT32);

  ml_tensors_info_get_tensor_dimension (in_res, 0, res_dim);
  EXPECT_TRUE (in_dim[0] == res_dim[0]);
  EXPECT_TRUE (in_dim[1] == res_dim[1]);
  EXPECT_TRUE (in_dim[2] == res_dim[2]);
  EXPECT_TRUE (in_dim[3] == res_dim[3]);

  /* output tensor in filter */
  status = ml_single_get_output_info (single, &out_res);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_tensors_info_get_count (out_res, &count);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_EQ (count, 1U);

  status = ml_tensors_info_get_tensor_name (out_res, 0, &name);
  EXPECT_EQ (status, ML_ERROR_NONE);
  g_free (name);

  status = ml_tensors_info_get_tensor_type (out_res, 0, &type);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_EQ (type, ML_TENSOR_TYPE_FLOAT32);

  ml_tensors_info_get_tensor_dimension (out_res, 0, res_dim);
  EXPECT_TRUE (out_dim[0] == res_dim[0]);
  EXPECT_TRUE (out_dim[1] == res_dim[1]);
  EXPECT_TRUE (out_dim[2] == res_dim[2]);
  EXPECT_TRUE (out_dim[3] == res_dim[3]);

  input = output = NULL;

  /* generate dummy data */
  status = ml_tensors_data_create (in_info, &input);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (input != NULL);

  status = ml_single_invoke (single, input, &output);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (output != NULL);

  status = ml_single_close (single);
  EXPECT_EQ (status, ML_ERROR_NONE);

  ml_tensors_data_destroy (output);
  ml_tensors_data_destroy (input);
  ml_tensors_info_destroy (in_res);
  ml_tensors_info_destroy (out_res);

  g_free (test_model);
  ml_tensors_info_destroy (in_info);
  ml_tensors_info_destroy (out_info);
}

/**
 * @brief Test NNStreamer single shot (snpe)
 * @detail invoke without tensor info
 */
TEST (nnstreamer_capi_singleshot, invoke_02)
{
  ml_single_h single;
  ml_tensors_info_h in_info;
  ml_tensors_data_h input, output;
  int status;

  const gchar *root_path = g_getenv ("NNSTREAMER_SOURCE_ROOT_PATH");
  gchar *test_model;

  /* supposed to run test in build directory */
  if (root_path == NULL)
    root_path = "..";

  test_model = g_build_filename (root_path, "tests", "test_models", "models",
      "inception_v3_quantized.dlc", NULL);
  ASSERT_TRUE (g_file_test (test_model, G_FILE_TEST_EXISTS));

  status = ml_single_open_full (&single, test_model, NULL, NULL,
      ML_NNFW_TYPE_SNPE, ML_NNFW_HW_ANY, RUNTIME_STRING_CPU);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_single_get_input_info (single, &in_info);
  EXPECT_EQ (status, ML_ERROR_NONE);

  input = output = NULL;

  /* generate dummy data */
  status = ml_tensors_data_create (in_info, &input);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (input != NULL);

  status = ml_single_invoke (single, input, &output);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (output != NULL);

  status = ml_single_close (single);
  EXPECT_EQ (status, ML_ERROR_NONE);

  ml_tensors_data_destroy (output);
  ml_tensors_data_destroy (input);
  ml_tensors_info_destroy (in_info);

  g_free (test_model);
}


/**
 * @brief get argmax from the float array
 */
void matchOutput (void *output_data, size_t size, size_t answer)
{
  size_t idx, max_idx = 0;

  float *array = (float *) output_data;
  float max_value = 0;
  for (idx = 0; idx < size / 4; idx++) {
    if (max_value < array[idx]) {
      max_idx = idx;
      max_value = array[idx];
    }
  }

  EXPECT_EQ (max_idx, answer);
}

/**
 * @brief Test NNStreamer single shot (snpe)
 * @detail test the image classification result
 */
TEST (nnstreamer_capi_singleshot, invoke_03_result)
{
  ml_single_h single;
  ml_tensors_info_h in_info;
  ml_tensors_data_h input, output;
  int status, fd;
  ssize_t data_read;
  size_t data_size;
  void *data = NULL;

  const gchar *root_path = g_getenv ("NNSTREAMER_SOURCE_ROOT_PATH");
  gchar *test_model, *data_file;

  /* supposed to run test in build directory */
  if (root_path == NULL)
    root_path = "..";

  test_model = g_build_filename (root_path, "tests", "test_models", "models",
      "inception_v3_quantized.dlc", NULL);
  ASSERT_TRUE (g_file_test (test_model, G_FILE_TEST_EXISTS));

  data_file = g_build_filename (
    root_path, "tests", "test_models", "data", "orange_299.raw", NULL);
  ASSERT_TRUE (g_file_test (data_file, G_FILE_TEST_EXISTS));

  fd = open (data_file, O_RDONLY);
  EXPECT_TRUE (fd >= 0);

  status = ml_single_open_full (&single, test_model, NULL, NULL,
      ML_NNFW_TYPE_SNPE, ML_NNFW_HW_ANY, RUNTIME_STRING_CPU);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_single_get_input_info (single, &in_info);
  EXPECT_EQ (status, ML_ERROR_NONE);

  input = output = NULL;

  /* generate dummy data */
  status = ml_tensors_data_create (in_info, &input);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (input != NULL);

  /** Load input data into the buffer */
  status = ml_tensors_data_get_tensor_data (input, 0, (void **) &data, &data_size);
  EXPECT_EQ (status, ML_ERROR_NONE);

  /** read the raw orange file (float type 299x299 RGB file) */
  status = lseek (fd, 0, SEEK_SET);
  EXPECT_EQ (status, 0);

  data_read = read (fd, data, data_size);
  EXPECT_EQ ((size_t) data_read, data_size);

  status = ml_single_invoke (single, input, &output);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (output != NULL);

  status = ml_tensors_data_get_tensor_data (output, 0, (void **)&data, &data_size);
  EXPECT_EQ (status, ML_ERROR_NONE);

  matchOutput (data, data_size, 951U); // idx for orange is 951

  if (fd >= 0)
    close (fd);
  data_file = g_build_filename (
    root_path, "tests", "test_models", "data", "plastic_cup.raw", NULL);
  ASSERT_TRUE (g_file_test (data_file, G_FILE_TEST_EXISTS));

  fd = open (data_file, O_RDONLY);
  EXPECT_TRUE (fd >= 0);

  /** Load input data into the buffer */
  status = ml_tensors_data_get_tensor_data (input, 0, (void **) &data, &data_size);
  EXPECT_EQ (status, ML_ERROR_NONE);

  /** read the raw plastic_cup file (float type 299x299 RGB file) */
  status = lseek (fd, 0, SEEK_SET);
  EXPECT_EQ (status, 0);

  data_read = read (fd, data, data_size);
  EXPECT_EQ ((size_t) data_read, data_size);

  status = ml_single_invoke (single, input, &output);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (output != NULL);

  status = ml_tensors_data_get_tensor_data (output, 0, (void **)&data, &data_size);
  EXPECT_EQ (status, ML_ERROR_NONE);

  matchOutput (data, data_size, 648U); // idx for plastic cup is 648

  status = ml_single_close (single);
  EXPECT_EQ (status, ML_ERROR_NONE);

  ml_tensors_data_destroy (output);
  ml_tensors_data_destroy (input);
  ml_tensors_info_destroy (in_info);

  g_free (test_model);
  g_free (data_file);
  if (fd >= 0)
    close (fd);
}

/**
 * Test pipeline API.
 * invoke with dummy input using videotestsrc.
 */
TEST (nnstreamer_capi_pipeline, invoke01)
{
  ml_pipeline_h handle = nullptr;
  const gchar *root_path = g_getenv ("NNSTREAMER_SOURCE_ROOT_PATH");
  gchar *pipeline;
  gchar *test_model;
  int status;
  ml_pipeline_state_e state;

  /* supposed to run test in build directory */
  if (root_path == NULL)
    root_path = "..";

  test_model = g_build_filename (root_path, "tests", "test_models", "models",
      "inception_v3_quantized.dlc", NULL);
  ASSERT_TRUE (g_file_test (test_model, G_FILE_TEST_EXISTS));

  pipeline = g_strdup_printf (
    "videotestsrc ! videoscale ! videoconvert ! video/x-raw,width=299,height=299,format=RGB,framerate=30/1 ! tensor_converter ! tensor_transform mode=arithmetic option=typecast:float32,add:-127.5,div:127.5 ! tensor_filter framework=snpe model=%s custom=%s latency=1 ! tensor_sink name=sink",
    test_model, RUNTIME_STRING_CPU);

  status = ml_pipeline_construct (pipeline, nullptr, nullptr, &handle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_pipeline_start (handle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_pipeline_get_state (handle, &state);
  EXPECT_EQ (status, ML_ERROR_NONE);
  wait_for_start (handle, state, status);
  EXPECT_EQ (state, ML_PIPELINE_STATE_PLAYING);

  g_usleep (1000000); /* let the pipeline run for 1s */

  status = ml_pipeline_stop (handle);
  EXPECT_EQ (status, ML_ERROR_NONE);
  g_usleep (500000); /* 500ms. Stopping pipeline needs some time */

  status = ml_pipeline_get_state (handle, &state);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_EQ (state, ML_PIPELINE_STATE_PAUSED);

  status = ml_pipeline_destroy (handle);
  EXPECT_EQ (status, ML_ERROR_NONE);
}

void
check_output (const ml_tensors_data_h data, const ml_tensors_info_h info, void *user_data)
{
  int status;
  size_t data_size;
  guint answer = *((guint *) user_data);

  status = ml_tensors_data_get_tensor_data (data, 0, (void **) &data, &data_size);
  EXPECT_EQ (status, ML_ERROR_NONE);

  matchOutput (data, data_size, answer);
}

/**
 * Test pipeline API.
 * invoke with appsrc (orange input)
 */
TEST (nnstreamer_capi_pipeline, invoke02)
{
  ml_pipeline_h handle = nullptr;
  const gchar *root_path = g_getenv ("NNSTREAMER_SOURCE_ROOT_PATH");
  gchar *pipeline;
  gchar *test_model;

  int status, fd;
  ssize_t data_read;
  size_t data_size;
  void *data = NULL;
  gchar *data_file;
  guint answer;

  ml_tensors_info_h in_info;
  ml_tensor_dimension in_dim;
  ml_tensors_data_h input = NULL;
  ml_pipeline_state_e state;
  ml_pipeline_src_h srchandle;
  ml_pipeline_sink_h sinkhandle;

  /* supposed to run test in build directory */
  if (root_path == NULL)
    root_path = "..";

  test_model = g_build_filename (root_path, "tests", "test_models", "models",
      "inception_v3_quantized.dlc", NULL);
  ASSERT_TRUE (g_file_test (test_model, G_FILE_TEST_EXISTS));

  /* declare pipeline */
  pipeline = g_strdup_printf (
    "appsrc name=srcx ! other/tensors,num_tensors=1,dimensions=3:299:299:1,types=float32,format=static,framerate=0/1 ! tensor_filter framework=snpe model=%s custom=Runtime:CPU custom=%s latency=1 ! tensor_sink name=sinkx sync=false async=false",
    test_model, RUNTIME_STRING_CPU);

  status = ml_pipeline_construct (pipeline, nullptr, nullptr, &handle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  answer = 951U;
  status = ml_pipeline_sink_register (handle, "sinkx", check_output, &answer, &sinkhandle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_pipeline_src_get_handle (handle, "srcx", &srchandle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  ml_tensors_info_create (&in_info);
  in_dim[0] = 3;
  in_dim[1] = 299;
  in_dim[2] = 299;
  in_dim[3] = 1;
  ml_tensors_info_set_count (in_info, 1);
  ml_tensors_info_set_tensor_type (in_info, 0, ML_TENSOR_TYPE_FLOAT32);
  ml_tensors_info_set_tensor_dimension (in_info, 0, in_dim);

  /* generate dummy data */
  status = ml_tensors_data_create (in_info, &input);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_TRUE (input != NULL);

  status = ml_pipeline_start (handle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  // g_usleep (100000); /* let the pipeline run for 100ms */

  data_file = g_build_filename (
    root_path, "tests", "test_models", "data", "orange_299.raw", NULL);
  ASSERT_TRUE (g_file_test (data_file, G_FILE_TEST_EXISTS));

  fd = open (data_file, O_RDONLY);
  EXPECT_TRUE (fd >= 0);

  /** Load input data into the buffer */
  status = ml_tensors_data_get_tensor_data (input, 0, (void **) &data, &data_size);
  EXPECT_EQ (status, ML_ERROR_NONE);

  /** read the raw orange file (float type 299x299 RGB file) */
  status = lseek (fd, 0, SEEK_SET);
  EXPECT_EQ (status, 0);

  data_read = read (fd, data, data_size);
  EXPECT_EQ ((size_t) data_read, data_size);

  /** push the image into appsrc */
  status = ml_pipeline_src_input_data (srchandle, input,
      ML_PIPELINE_BUF_POLICY_DO_NOT_FREE);
  EXPECT_EQ (status, ML_ERROR_NONE);

  g_usleep (1000000); /* let the pipeline run for 1s */

  status = ml_pipeline_stop (handle);
  EXPECT_EQ (status, ML_ERROR_NONE);
  g_usleep (500000); /* 500ms. Stopping pipeline needs some time */

  status = ml_pipeline_get_state (handle, &state);
  EXPECT_EQ (status, ML_ERROR_NONE);
  EXPECT_EQ (state, ML_PIPELINE_STATE_PAUSED);

  status = ml_pipeline_src_release_handle (srchandle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_pipeline_sink_unregister (sinkhandle);
  EXPECT_EQ (status, ML_ERROR_NONE);

  status = ml_pipeline_destroy (handle);
  EXPECT_EQ (status, ML_ERROR_NONE);
}

/**
 * @brief Main gtest
 */
int
main (int argc, char **argv)
{
  int result = -1;

  try {
    testing::InitGoogleTest (&argc, argv);
  } catch (...) {
    g_warning ("catch 'testing::internal::<unnamed>::ClassUniqueToAlwaysTrue'");
  }

  /* ignore tizen feature status while running the testcases */
  set_feature_state (SUPPORTED);

  try {
    result = RUN_ALL_TESTS ();
  } catch (...) {
    g_warning ("catch `testing::internal::GoogleTestFailureException`");
  }

  set_feature_state (NOT_CHECKED_YET);

  return result;
}

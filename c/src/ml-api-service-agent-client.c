/* SPDX-License-Identifier: Apache-2.0 */
/**
 * Copyright (c) 2022 Samsung Electronics Co., Ltd. All Rights Reserved.
 *
 * @file ml-api-service-agent-client.c
 * @date 20 Jul 2022
 * @brief agent (dbus) implementation of NNStreamer/Service C-API
 * @see https://github.com/nnstreamer/nnstreamer
 * @author Yongjoo Ahn <yongjoo1.ahn@samsung.com>
 * @bug No known bugs except for NYI items
 */

#include <glib/gstdio.h>
#include <json-glib/json-glib.h>

#include "ml-api-internal.h"
#include "ml-api-service-private.h"
#include "ml-api-service.h"

#define WARN_MSG_DPTR_SET_OVER "The memory blocks pointed by pipeline_desc will be set over with a new one.\n" \
        "It is highly suggested that `%s` before it is set."

#if defined(__TIZEN__)
#include <app_common.h>

/**
 * @brief Get Tizen application info. It should be a json string.
 */
static gchar *
_get_app_info (void)
{
  g_autofree gchar *app_id = NULL;
  g_autoptr (JsonBuilder) builder = NULL;
  g_autoptr (JsonGenerator) gen = NULL;
  int ret;

  ret = app_get_id (&app_id);
  if (ret == APP_ERROR_INVALID_CONTEXT) {
    /* Not a Tizen APP context, e.g. gbs build test */
    _ml_logi ("Not an APP context, skip creating app_info.");
    return NULL;
  }

  /**
   * @todo Check whether the given path is in the app's resource directory.
   * Below is sample code for this (unfortunately, TCT get error with it):
   * g_autofree gchar *app_resource_path = NULL;
   * g_autofree gchar *app_shared_resource_path = NULL;
   * app_resource_path = app_get_resource_path ();
   * app_shared_resource_path = app_get_shared_resource_path ();
   * if (!app_resource_path || !app_shared_resource_path) {
   *   _ml_error_report_return (ML_ERROR_PERMISSION_DENIED,
   *      "Failed to get the app resource path of the caller.");
   * }
   * if (!g_str_has_prefix (path, app_resource_path) &&
   *     !g_str_has_prefix (path, app_shared_resource_path)) {
   *   _ml_error_report_return (ML_ERROR_PERMISSION_DENIED,
   *      "The model file '%s' is not in the app's resource directory.", path);
   * }
   */
  builder = json_builder_new ();
  json_builder_begin_object (builder);

  json_builder_set_member_name (builder, "is_rpk");
  json_builder_add_string_value (builder, "F");

  json_builder_set_member_name (builder, "app_id");
  json_builder_add_string_value (builder, app_id);

  json_builder_end_object (builder);

  gen = json_generator_new ();
  json_generator_set_root (gen, json_builder_get_root (builder));
  json_generator_set_pretty (gen, TRUE);

  return json_generator_to_data (gen, NULL);
}
#else
#define _get_app_info(...) (NULL)
#endif

/**
 * @brief Build ml_information_h from json cstring.
 */
static gint
_build_ml_info_from_json_cstr (const gchar * jcstring, void **handle)
{
  g_autoptr (GError) err = NULL;
  g_autoptr (JsonParser) parser = NULL;
  g_autoptr (GList) members = NULL;
  ml_information_list_h _info_list = NULL;
  ml_information_h _info = NULL;
  JsonNode *rnode = NULL;
  JsonArray *array = NULL;
  JsonObject *jobj = NULL;
  GList *l;
  gint ret = ML_ERROR_NONE;
  guint i, n;

  if (!handle) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The argument for ml-information should not be NULL.");
  }

  if (NULL != *handle) {
    _ml_logw (WARN_MSG_DPTR_SET_OVER, "info");
    *handle = NULL;
  }

  parser = json_parser_new ();
  if (!parser) {
    _ml_error_report_return (ML_ERROR_OUT_OF_MEMORY,
        "Failed to allocate memory for JsonParser. Out of memory?");
  }

  if (!json_parser_load_from_data (parser, jcstring, -1, &err)) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "Failed to parse the json string (%s).",
        err ? err->message : "Unknown error");
  }

  rnode = json_parser_get_root (parser);
  if (!rnode) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "Failed to get the root node of json string.");
  }

  if (JSON_NODE_HOLDS_ARRAY (rnode)) {
    array = json_node_get_array (rnode);
    n = (array) ? json_array_get_length (array) : 0U;
  } else {
    n = 1U;
  }

  if (n == 0U) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "Failed to retrieve the length of the json array.");
  }

  if (array)
    ret = _ml_information_list_create (&_info_list);
  else
    ret = _ml_information_create (&_info);
  if (ML_ERROR_NONE != ret) {
    _ml_error_report ("Failed to parse app_info, cannot create info handle.");
    goto done;
  }

  for (i = 0; i < n; i++) {
    if (array) {
      jobj = json_array_get_object_element (array, i);
      ret = _ml_information_create (&_info);
      if (ML_ERROR_NONE != ret) {
        _ml_error_report
            ("Failed to parse app_info, cannot create info handle.");
        goto done;
      }

      _ml_information_list_add (_info_list, _info);
    } else {
      jobj = json_node_get_object (rnode);
    }

    members = json_object_get_members (jobj);
    for (l = members; l != NULL; l = l->next) {
      const gchar *key = l->data;
      const gchar *val = _ml_service_get_json_string_member (jobj, key);

      /* Prevent empty string case. */
      if (STR_IS_VALID (key) && STR_IS_VALID (val)) {
        ret = _ml_information_set (_info, key, g_strdup (val), g_free);
        if (ret != ML_ERROR_NONE) {
          _ml_error_report ("Failed to append app info to the info handle.");
          goto done;
        }
      }
    }
  }

done:
  if (ret == ML_ERROR_NONE) {
    *handle = (array) ? _info_list : _info;
  } else {
    if (_info_list)
      ml_information_list_destroy (_info_list);
    else if (_info)
      ml_information_destroy (_info);
  }

  return ret;
}

/**
 * @brief Internal function to check the path of model or resource.
 */
static int
_ml_service_check_path (const char *path)
{
  int ret;
  g_autofree gchar *dir_name = NULL;
  GStatBuf statbuf;

  if (!path) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'path' is NULL. It should be a valid string.");
  }

  if (g_file_test (path, G_FILE_TEST_IS_DIR))
    dir_name = g_strdup (path);
  else
    dir_name = g_path_get_dirname (path);

  ret = g_stat (dir_name, &statbuf);
  if (ret != 0) {
    _ml_error_report_return (ML_ERROR_PERMISSION_DENIED,
        "Failed to get the information of given path '%s'.", path);
  }

  if (!g_path_is_absolute (path)
      || !g_file_test (path, (G_FILE_TEST_EXISTS | G_FILE_TEST_IS_REGULAR))
      || g_file_test (path, G_FILE_TEST_IS_SYMLINK)) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "Given path '%s' is not a regular.", path);
  }

  return ML_ERROR_NONE;
}

/**
 * @brief Set the pipeline description with a given name.
 */
int
ml_service_pipeline_set (const char *name, const char *pipeline_desc)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (!pipeline_desc) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'pipeline_desc' is NULL. It should be a valid string.");
  }

  ret = ml_agent_pipeline_set_description (name, pipeline_desc);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method set_pipeline.");
  }

  return ret;
}

/**
 * @brief Get the pipeline description with a given name.
 */
int
ml_service_pipeline_get (const char *name, char **pipeline_desc)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL, It should be a valid string.");
  }

  if (pipeline_desc == NULL) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The argument for 'pipeline_desc' should not be NULL.");
  }

  if (*pipeline_desc != NULL) {
    _ml_logw (WARN_MSG_DPTR_SET_OVER, "char *pipeline_desc = NULL");
    *pipeline_desc = NULL;
  }

  ret = ml_agent_pipeline_get_description (name, pipeline_desc);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method get_pipeline.");
  }

  return ret;
}

/**
 * @brief Delete the pipeline description with a given name.
 */
int
ml_service_pipeline_delete (const char *name)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL, It should be a valid string.");
  }

  ret = ml_agent_pipeline_delete (name);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method delete_pipeline.");
  }

  return ret;
}

/**
 * @brief Launch the pipeline of given service.
 */
int
ml_service_pipeline_launch (const char *name, ml_service_h * handle)
{
  int ret = ML_ERROR_NONE;
  ml_service_s *mls;
  _ml_service_server_s *server;

  check_feature_state (ML_FEATURE_SERVICE);

  if (handle == NULL) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The argument for 'handle' should not be NULL.");
  }

  if (*handle != NULL) {
    _ml_logw (WARN_MSG_DPTR_SET_OVER, "ml_service_h *handle = NULL");
  }
  *handle = NULL;

  mls = _ml_service_create_internal (ML_SERVICE_TYPE_SERVER_PIPELINE);
  if (mls == NULL) {
    _ml_error_report_return (ML_ERROR_OUT_OF_MEMORY,
        "Failed to allocate memory for the service handle. Out of memory?");
  }

  mls->priv = server = g_try_new0 (_ml_service_server_s, 1);
  if (server == NULL) {
    _ml_service_destroy_internal (mls);
    _ml_error_report_return (ML_ERROR_OUT_OF_MEMORY,
        "Failed to allocate memory for the service handle's private data. Out of memory?");
  }

  ret = ml_agent_pipeline_launch (name, &(server->id));
  if (ret < 0) {
    _ml_service_destroy_internal (mls);
    _ml_error_report_return (ret,
        "Failed to invoke the method launch_pipeline.");
  }

  server->service_name = g_strdup (name);
  *handle = mls;

  return ML_ERROR_NONE;
}

/**
 * @brief Return state of given ml_service_h
 */
int
ml_service_pipeline_get_state (ml_service_h handle, ml_pipeline_state_e * state)
{
  int ret = ML_ERROR_NONE;
  gint _state = ML_PIPELINE_STATE_UNKNOWN;
  ml_service_s *mls = (ml_service_s *) handle;
  _ml_service_server_s *server;

  check_feature_state (ML_FEATURE_SERVICE);

  if (NULL == state) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter 'state' should not be NULL.");
  }
  *state = ML_PIPELINE_STATE_UNKNOWN;

  if (!_ml_service_handle_is_valid (mls)) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'handle' (ml_service_h), is invalid. It should be a valid ml_service_h instance.");
  }

  server = (_ml_service_server_s *) mls->priv;
  ret = ml_agent_pipeline_get_state (server->id, &_state);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method get_state.");
  }

  *state = (ml_pipeline_state_e) _state;
  return ret;
}

/**
 * @brief Internal function to release ml-service pipeline data.
 */
int
_ml_service_pipeline_release_internal (ml_service_s * mls)
{
  _ml_service_server_s *server = (_ml_service_server_s *) mls->priv;
  int ret;

  /* Supposed internal function call to release handle. */
  if (!server)
    return ML_ERROR_NONE;

  if (server->id > 0) {
    ret = ml_agent_pipeline_destroy (server->id);
    if (ret < 0) {
      _ml_error_report_return (ret,
          "Failed to invoke the method destroy_pipeline.");
    }
  }

  g_free (server->service_name);
  g_free (server);
  mls->priv = NULL;

  return ML_ERROR_NONE;
}

/**
 * @brief Registers new information of a neural network model.
 */
int
ml_service_model_register (const char *name, const char *path,
    const bool activate, const char *description, unsigned int *version)
{
  int ret = ML_ERROR_NONE;
  g_autofree gchar *app_info = NULL;
  g_autofree gchar *converted = NULL;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (NULL == version) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter 'version' should not be NULL.");
  }
  *version = 0U;

  converted = _ml_convert_predefined_entity (path);
  ret = _ml_service_check_path (converted);
  if (ret != ML_ERROR_NONE)
    return ret;

  app_info = _get_app_info ();

  ret = ml_agent_model_register (name, converted, activate,
      description ? description : "", app_info ? app_info : "", version);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method model_register.");
  }

  return ret;
}

/**
 * @brief Updates the description of neural network model with given @a name and @a version.
 */
int
ml_service_model_update_description (const char *name,
    const unsigned int version, const char *description)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (version == 0U) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'version' is 0. It should be a valid unsigned int.");
  }

  if (!description) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'description' is NULL. It should be a valid string.");
  }

  ret = ml_agent_model_update_description (name, version, description);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method model_update_description.");
  }

  return ret;
}

/**
 * @brief Activates a neural network model with given @a name and @a version.
 */
int
ml_service_model_activate (const char *name, const unsigned int version)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (version == 0U) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'version' is 0. It should be a valid unsigned int.");
  }

  ret = ml_agent_model_activate (name, version);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method model_activate.");
  }

  return ret;
}

/**
 * @brief Gets the information of neural network model with given @a name and @a version.
 */
int
ml_service_model_get (const char *name, const unsigned int version,
    ml_information_h * info)
{
  int ret = ML_ERROR_NONE;
  ml_information_h _info = NULL;
  g_autofree gchar *description = NULL;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (info == NULL) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The argument for 'info' should not be NULL.");
  }

  if (*info != NULL) {
    _ml_logw (WARN_MSG_DPTR_SET_OVER, "ml_information_h info = NULL");
  }
  *info = NULL;

  ret = ml_agent_model_get (name, version, &description);
  if (ML_ERROR_NONE != ret || !description) {
    _ml_error_report ("Failed to invoke the method model_get.");
    return ret;
  }

  ret = _build_ml_info_from_json_cstr (description, &_info);
  if (ML_ERROR_NONE != ret)
    _ml_error_report ("Failed to convert json string to ml-information.");
  else
    *info = _info;

  return ret;
}

/**
 * @brief Gets the information of activated neural network model with given @a name.
 */
int
ml_service_model_get_activated (const char *name, ml_information_h * info)
{
  int ret = ML_ERROR_NONE;

  ml_information_h _info = NULL;
  g_autofree gchar *description = NULL;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (info == NULL) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The argument for 'info' should not be NULL.");
  }

  if (*info != NULL) {
    _ml_logw (WARN_MSG_DPTR_SET_OVER, "ml_information_h info = NULL");
  }
  *info = NULL;

  ret = ml_agent_model_get_activated (name, &description);
  if (ML_ERROR_NONE != ret || !description) {
    _ml_error_report ("Failed to invoke the method model_get_activated.");
    return ret;
  }

  ret = _build_ml_info_from_json_cstr (description, &_info);
  if (ML_ERROR_NONE != ret)
    _ml_error_report ("Failed to convert json string to ml_information_h.");
  else
    *info = _info;

  return ret;
}

/**
 * @brief Gets the list of neural network model with given @a name.
 */
int
ml_service_model_get_all (const char *name, ml_information_list_h * info_list)
{
  g_autofree gchar *description = NULL;
  ml_information_list_h _info_list = NULL;
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (NULL == info_list) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter 'info_list' should not be NULL.");
  }
  *info_list = NULL;

  ret = ml_agent_model_get_all (name, &description);
  if (ML_ERROR_NONE != ret || !description) {
    _ml_error_report_return (ret, "Failed to invoke the method model_get_all.");
  }

  ret = _build_ml_info_from_json_cstr (description, &_info_list);
  if (ML_ERROR_NONE != ret)
    _ml_error_report ("Failed to convert json string to ml-information list.");
  else
    *info_list = _info_list;

  return ret;
}

/**
 * @brief Deletes a model information with given @a name and @a version from machine learning service.
 */
int
ml_service_model_delete (const char *name, const unsigned int version)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  ret = ml_agent_model_delete (name, version, FALSE);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method model_delete.");
  }

  return ret;
}

/**
 * @brief Adds new information of machine learning resources.
 */
int
ml_service_resource_add (const char *name, const char *path,
    const char *description)
{
  int ret = ML_ERROR_NONE;
  g_autofree gchar *app_info = NULL;
  g_autofree gchar *converted = NULL;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  converted = _ml_convert_predefined_entity (path);
  ret = _ml_service_check_path (converted);
  if (ret != ML_ERROR_NONE)
    return ret;

  app_info = _get_app_info ();

  ret = ml_agent_resource_add (name, converted, description ? description : "",
      app_info ? app_info : "");
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method resource_add.");
  }

  return ret;
}

/**
 * @brief Deletes the information of the resources from machine learning service.
 */
int
ml_service_resource_delete (const char *name)
{
  int ret = ML_ERROR_NONE;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  ret = ml_agent_resource_delete (name);
  if (ret < 0) {
    _ml_error_report ("Failed to invoke the method resource_delete.");
  }

  return ret;
}

/**
 * @brief Gets the information of the resources from machine learning service.
 */
int
ml_service_resource_get (const char *name, ml_information_list_h * res)
{
  int ret = ML_ERROR_NONE;
  ml_information_list_h _info_list = NULL;
  g_autofree gchar *res_info = NULL;

  check_feature_state (ML_FEATURE_SERVICE);

  if (!name) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The parameter, 'name' is NULL. It should be a valid string.");
  }

  if (res == NULL) {
    _ml_error_report_return (ML_ERROR_INVALID_PARAMETER,
        "The argument for 'res' should not be NULL.");
  }

  if (*res != NULL) {
    _ml_logw (WARN_MSG_DPTR_SET_OVER, "ml_information_list_h res = NULL");
  }
  *res = NULL;

  ret = ml_agent_resource_get (name, &res_info);
  if (ML_ERROR_NONE != ret || !res_info) {
    _ml_error_report_return (ret, "Failed to invoke the method resource_get.");
  }

  ret = _build_ml_info_from_json_cstr (res_info, &_info_list);
  if (ML_ERROR_NONE != ret)
    _ml_error_report ("Failed to convert json string to ml-information list.");
  else
    *res = _info_list;

  return ret;
}

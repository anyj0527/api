# Install path for unittest
unittest_base_dir = join_paths(api_install_bindir, 'unittest-ml')
unittest_install_dir = join_paths(unittest_base_dir,'tests')

# Set dependency and test-env
testenv = environment()
testenv.set('MLAPI_SOURCE_ROOT_PATH', meson.source_root())
testenv.set('MLAPI_BUILD_ROOT_PATH', meson.build_root())

unittest_util_static = static_library('unittest_util',
  files('capi/unittest_util.c'),
  dependencies: [glib_dep],
  install: false
)

unittest_common_dep = declare_dependency(
  link_with: [unittest_util_static],
  dependencies: [nns_capi_dep, gtest_dep],
  compile_args: ['-DFAKEDLOG=1'],
  include_directories: nns_capi_include
)

if get_option('enable-ml-service')
  ml_agent_lib_objs = ml_agent_lib.extract_objects(ml_agent_lib_srcs)
  ml_agent_test_both_lib = both_libraries('ml-agent-test',
    dependencies: ml_agent_deps,
    include_directories: ml_agent_incs,
    install: true,
    install_dir: ml_agent_install_libdir,
    cpp_args: ['-DDB_PATH="."', ml_agent_db_key_prefix_arg],
    objects: ml_agent_lib_objs,
    version: ml_agent_version,
    pic: true
  )

  ml_agent_test_lib = ml_agent_test_both_lib.get_shared_lib()
  if get_option('default_library') == 'static'
    ml_agent_test_lib = ml_agent_test_both_lib.get_static_lib()
  endif

  ml_agent_test_dep = declare_dependency(
    dependencies: ml_agent_deps,
    include_directories: ml_agent_incs,
    link_with: ml_agent_test_lib
  )

  ml_agent_main_objs = ml_agent_executable.extract_objects(ml_agent_main_file)
  executable('machine-learning-agent-test',
    dependencies: ml_agent_test_dep,
    install: true,
    install_dir: unittest_base_dir,
    c_args: ['-DDB_PATH="."', ml_agent_db_key_prefix_arg],
    objects: ml_agent_main_objs
  )

  subdir('services')
  subdir('daemon')
  subdir('plugin-parser')
endif

subdir('capi')

if get_option('install-test')
  install_subdir('test_models', install_dir: unittest_install_dir)
endif

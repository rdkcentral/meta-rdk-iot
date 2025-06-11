#FAQ: Why is this a function?
#A: Matter's CMake (chip_gn.cmake et al) wants to set variables
#in the parent context. If this were just stuck in a CMakeLists.txt,
#there would be no parent context to set variables in at include().

function(barton_build MATTER_CONF_DIR)

    include(CMakeGenerateNinja.cmake)
    include(ExternalProject)

    get_filename_component(MATTER_ROOT ../../ ABSOLUTE BASE_DIR ${CMAKE_CURRENT_LIST_DIR})

    if (CMAKE_BUILD_TYPE STREQUAL "Debug")
        set(CONFIG_CHIP_DEBUG YES)
    else()
        set(CONFIG_CHIP_DEBUG NO)
    endif()

    set(GN_ROOT_TARGET ${CMAKE_CURRENT_LIST_DIR})

    #-std=gnu++14 defines linux, but that macro is not used in the SDK. To avoid annoying
    #headaches with the preprocessor replacing any mention of 'linux' with 1, just don't
    #bother defining this at all. The standard (c++14) macros are __linux and __linux__.
    list(APPEND CONFIG_FLAGS "-Ulinux")

    set(COMMON_CMAKE_SOURCE_DIR "${MATTER_ROOT}/config/common/cmake")
    include(${COMMON_CMAKE_SOURCE_DIR}/util.cmake)
    include(${COMMON_CMAKE_SOURCE_DIR}/chip_gn_args.cmake)
    include(${COMMON_CMAKE_SOURCE_DIR}/chip_gn.cmake)

    matter_add_cflags("${CONFIG_FLAGS} ${CMAKE_C_FLAGS} ")
    matter_add_cxxflags("${CONFIG_FLAGS} ${CMAKE_CXX_FLAGS} ")

    execute_process(COMMAND ${CMAKE_CURRENT_LIST_DIR}/activate.sh)

    configure_file(${CMAKE_CURRENT_LIST_DIR}/BartonProjectConfig.in BartonProjectConfig.h @ONLY)

    matter_common_gn_args(
        DEBUG       CONFIG_CHIP_DEBUG
    )

    matter_add_gn_arg_string("chip_project_config_include"               "<BartonProjectConfig.h>")
    matter_add_gn_arg_string("chip_system_project_config_include"        "<BartonProjectConfig.h>")

    gn_host_cpu_from_cmake()

    if (NOT GN_SYSTEM_PROCESSOR)
        message(FATAL_ERROR "Matter enabled but no Generate Ninja architecture defined for ${CMAKE_SYSTEM_PROCESSOR}")
    endif()

    if (CMAKE_SYSROOT)
        matter_add_gn_arg_string("sysroot" ${CMAKE_SYSROOT})
    endif()

    if (CMAKE_PREFIX_PATH)
        set(ENV{PKG_CONFIG_LIBDIR} ${CMAKE_PREFIX_PATH}/lib/pkgconfig)
    endif()

    matter_add_gn_arg_string("custom_toolchain" //build/toolchain/custom)
    matter_add_gn_arg("chip_project_config_include_dirs" ["${CMAKE_CURRENT_BINARY_DIR}"])
    matter_add_gn_arg_string("chip_code_pre_generated_directory" "${CMAKE_CURRENT_LIST_DIR}/zzz_generated")

    matter_add_gn_arg_string("target_os" "linux")
    matter_add_gn_arg_string("target_cpu" ${GN_SYSTEM_PROCESSOR})
    matter_add_gn_arg_string("target_cc" ${CMAKE_C_COMPILER})
    matter_add_gn_arg_string("target_cxx" ${CMAKE_CXX_COMPILER})
    matter_add_gn_arg_string("target_ar" ${CMAKE_AR})
    matter_add_gn_arg_bool("enable_rtti" true)

    matter_add_gn_arg_string("chip_system_config_locking" "posix")
    matter_add_gn_arg_string("chip_stack_lock_tracking" "fatal")
    matter_add_gn_arg_string("chip_mdns" "minimal")
    matter_add_gn_arg_string("chip_device_platform" "linux")
    matter_add_gn_arg_bool("chip_build_tools" false)
    matter_add_gn_arg_bool("chip_build_tests" false)
    matter_add_gn_arg_bool("chip_enable_ble" true)
    matter_add_gn_arg_bool("chip_enable_openthread" true)
    matter_add_gn_arg_bool("chip_openthread_border_router" true)
    matter_add_gn_arg_bool("chip_enable_wifi" false)
    matter_add_gn_arg_bool("chip_inet_config_enable_ipv4" false)
    matter_add_gn_arg_bool("chip_system_config_use_lwip" false)
    matter_add_gn_arg_bool("chip_system_config_use_sockets" true)
    matter_add_gn_arg_bool("chip_with_lwip" false)
    matter_add_gn_arg_bool("chip_enable_access_restrictions" true)

    matter_generate_args_tmp_file()

    set(BARTON_MATTER_TARGET matter-barton)
    set(BARTON_MATTER_TARGET ${BARTON_MATTER_TARGET} PARENT_SCOPE)
    matter_build(${BARTON_MATTER_TARGET})

    set(MATTER_HEADER_DESTINATION include/matter)

    get_target_property(MATTER_INCLUDE_DIRECTORIES ${BARTON_MATTER_TARGET} INTERFACE_INCLUDE_DIRECTORIES)
    list(APPEND MATTER_INCLUDE_DIRECTORIES ${MATTER_ROOT}/third_party/barton/zzz_generated/third_party/barton/barton/zap/app-templates)
    list(APPEND MATTER_INCLUDE_DIRECTORIES ${MATTER_ROOT}/third_party/inipp/repo/inipp)

    foreach(MATTER_INCLUDE_DIR ${MATTER_INCLUDE_DIRECTORIES})

    # Some src/ directories have symbolic links that will break on install,
    # and aren't usefulto this project anyway.
    install(
            DIRECTORY ${MATTER_INCLUDE_DIR}/
            DESTINATION include/matter
            FILES_MATCHING PATTERN *.h*
                PATTERN test_driver EXCLUDE
                PATTERN darwin EXCLUDE
    )
    endforeach()

    install(FILES ${MATTER_LIBRARIES} DESTINATION lib)
    install(FILES ${CMAKE_CURRENT_BINARY_DIR}/lib/libBartonMatter.a DESTINATION lib)
    install(FILES ${CMAKE_CURRENT_BINARY_DIR}/BartonProjectConfig.h DESTINATION ${MATTER_HEADER_DESTINATION})

    file(GLOB_RECURSE BARTON_HEADERS ${MATTER_ROOT}/third_party/barton/*.h*)
    install(FILES ${BARTON_HEADERS} DESTINATION ${MATTER_HEADER_DESTINATION})

endfunction()

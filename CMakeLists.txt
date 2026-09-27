cmake_minimum_required(VERSION 3.15)
project(cpustress C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

if(NOT MSVC)
    add_compile_options(-O2 -Wall -Wextra)
endif()

if(WIN32)
    set(PLATFORM_SRC native/platform_win.c)
else()
    set(PLATFORM_SRC native/platform_posix.c)
endif()

set(ENGINE_SRC native/stress_engine.c ${PLATFORM_SRC})

if(NOT WIN32)
    find_package(Threads REQUIRED)
endif()

find_package(Java REQUIRED COMPONENTS Development)

if("$ENV{JAVA_HOME}" STREQUAL "")
    get_filename_component(_JAVAC_REAL "${Java_JAVAC_EXECUTABLE}" REALPATH)
    get_filename_component(_JAVAC_BIN_DIR "${_JAVAC_REAL}" DIRECTORY)
    get_filename_component(_JAVA_HOME_GUESS "${_JAVAC_BIN_DIR}" DIRECTORY)

    if(NOT EXISTS "${_JAVA_HOME_GUESS}/include/jni.h")
        message(FATAL_ERROR
            "Nao foi possivel localizar o JDK a partir de javac "
            "('${Java_JAVAC_EXECUTABLE}' -> '${_JAVAC_REAL}'): "
            "'${_JAVA_HOME_GUESS}/include/jni.h' nao existe. "
            "Defina JAVA_HOME manualmente apontando para a instalacao do JDK "
            "(diretorio que contem include/jni.h) e rode o CMake novamente.")
    endif()

    set(ENV{JAVA_HOME} "${_JAVA_HOME_GUESS}")
endif()

find_package(JNI REQUIRED)

set(JAVA_SRC_DIR ${CMAKE_SOURCE_DIR}/java)
set(GEN_DIR ${CMAKE_BINARY_DIR}/generated)

# Gera o header JNI a partir de CpuStress.java antes de compilar a ponte.
add_custom_command(
    OUTPUT ${GEN_DIR}/br_furb_so_stress_jni_CpuStress.h
    COMMAND ${CMAKE_COMMAND} -E make_directory ${GEN_DIR}
    COMMAND ${Java_JAVAC_EXECUTABLE}
            -h ${GEN_DIR}
            -d ${CMAKE_BINARY_DIR}/classes
            ${JAVA_SRC_DIR}/br/furb/so/stress/jni/CpuStress.java
    DEPENDS ${JAVA_SRC_DIR}/br/furb/so/stress/jni/CpuStress.java
    COMMENT "Gerando header JNI")

add_custom_target(jni_header DEPENDS ${GEN_DIR}/br_furb_so_stress_jni_CpuStress.h)

add_library(cpustress SHARED native/jni_bridge.c ${ENGINE_SRC})
add_dependencies(cpustress jni_header)
target_include_directories(cpustress PRIVATE
    native/include ${GEN_DIR} ${JNI_INCLUDE_DIRS})

if(NOT WIN32)
    target_link_libraries(cpustress PRIVATE Threads::Threads m)
endif()

set_target_properties(cpustress PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

# GLOB_RECURSE porque as classes estao divididas em subpacotes (jni,
# metrics, cli, ui).
file(GLOB_RECURSE JAVA_SOURCES CONFIGURE_DEPENDS
     ${JAVA_SRC_DIR}/br/furb/so/stress/*.java)

add_custom_target(java ALL
    COMMAND ${Java_JAVAC_EXECUTABLE}
            -d ${CMAKE_BINARY_DIR}/classes
            ${JAVA_SOURCES}
    DEPENDS ${JAVA_SOURCES}
    COMMENT "Compilando classes Java")

add_dependencies(java jni_header)

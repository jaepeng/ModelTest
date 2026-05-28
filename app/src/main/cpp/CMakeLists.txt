cmake_minimum_required(VERSION 3.22.1)
project("modeltest")
include_directories(${CMAKE_SOURCE_DIR}/include)

# 你的 JNI 桥接代码
add_library(
        modeltest
        SHARED
        llama_wrapper.cpp
)

# 定义项目根目录（关键！）
get_filename_component(PROJECT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../ ABSOLUTE)

# 1. 链接 libllama.so
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
        IMPORTED_LOCATION ${PROJECT_ROOT}/src/main/jniLibs/arm64-v8a/libllama.so
)

# 2. 链接 libggml.so
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
        IMPORTED_LOCATION ${PROJECT_ROOT}/src/main/jniLibs/arm64-v8a/libggml.so
)

# 3. 链接其他依赖
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
        IMPORTED_LOCATION ${PROJECT_ROOT}/src/main/jniLibs/arm64-v8a/libggml-base.so
)

# 链接系统库
find_library(log-lib log)

# 把所有库都链接起来
target_link_libraries(
        modeltest
        llama
        ggml
        ggml-base
        # 使用 -L 和 -l 避免没有 SONAME 的库被写入绝对路径
        -L${PROJECT_ROOT}/src/main/jniLibs/arm64-v8a
        -lggml-cpu-android_armv8.2_1
        ${log-lib}
        android
)

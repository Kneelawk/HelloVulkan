package com.kneelawk.spirv

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet

import java.nio.file.Path
import java.nio.file.Paths

class SpirvCompilerPlugin implements Plugin<Project> {
    Project project

    void apply(Project project) {
        this.project = project

        addSourceSetExtensions()
        addCompileTasks()
    }

    void addSourceSetExtensions() {
        project.sourceSets.all { sourceSet ->
            String name = sourceSet.name
            SourceDirectorySet sds = project.objects.sourceDirectorySet(name, "$name Spirv source")
            sourceSet.extensions.add('spirv', sds)
            sds.srcDir("src/$name/spirv")
            sds.include("**/*.glsl")
            sds.outputDir = new File(project.buildDir, "spirv/$name")
        }
    }

    void addCompileTasks() {
        project.sourceSets.all { sourceSet ->
            addCompileTask(sourceSet)
        }
    }

    void addCompileTask(SourceSet sourceSet) {
        project.task([type: SpirvCompile], sourceSet.name == 'main' ? 'compileSpirv' : "compile${sourceSet.name.capitalize()}Spirv") {
            inputFiles = project.files(sourceSet.spirv.files)
            moduleName = "${sourceSet.name}.spv"
            modulePackage = getCommonPackage(sourceSet.spirv.srcDirs, sourceSet.spirv.files)
            outputDir = sourceSet.spirv.outputDir

            description = "Compiles ${sourceSet.name} SPIR-V source."
        }
    }

    static String getCommonPackage(Set<File> srcDirs, Set<File> files) {
        // part of this code was taken from RosettaCode
        // https://rosettacode.org/wiki/Find_common_directory_path#Groovy
        return files.collect { file ->
            Path filePath = file.toPath().getParent()
            Path packagePath = Paths.get('')
            for (File dir : srcDirs) {
                Path dirPath = dir.toPath()
                if (filePath.startsWith(dirPath)) {
                    packagePath = dirPath.relativize(filePath)
                }
            }
            packagePath.asList()
        }.transpose().inject([match: true, commonParts: []]) { result, part ->
            result.match = result.match && part.every { it == part[0] }
            if (result.match) {
                result.commonParts << part[0]
            }
            result
        }.commonParts.join('.')
    }
}
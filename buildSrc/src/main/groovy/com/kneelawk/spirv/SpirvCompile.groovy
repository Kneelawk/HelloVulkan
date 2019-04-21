package com.kneelawk.spirv

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory

import javax.inject.Inject

class SpirvCompile extends DefaultTask {
    private ExecAction execAction = execActionFactory.newExecAction()

    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    FileCollection inputFiles

    @Input
    Property<String> moduleName = objectFactory.property(String)

    @Input
    Property<String> modulePackage = objectFactory.property(String)

    @OutputDirectory
    DirectoryProperty outputDir = objectFactory.directoryProperty()

    @Inject
    protected ExecActionFactory getExecActionFactory() {
        throw new UnsupportedOperationException()
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException()
    }

    @TaskAction
    void execute() {
        Directory outputPackageDir = outputDir.dir(modulePackage.map { pac ->
            pac.replace('.', '/')
        }).get()

        if (!outputPackageDir.asFile.exists()) {
            outputPackageDir.asFile.mkdirs()
        }

        execAction.executable('glslangValidator')
        execAction.workingDir(project.rootDir)

        execAction.args('-V')
        execAction.args(inputFiles.files)
        execAction.args('-o', outputPackageDir.file(moduleName).get())

        ByteArrayOutputStream standardOutputBuf = new ByteArrayOutputStream()
        execAction.standardOutput = new TeeOutputStream(execAction.standardOutput, standardOutputBuf)

        ByteArrayOutputStream errorOutputBuf = new ByteArrayOutputStream()
        execAction.errorOutput = new TeeOutputStream(execAction.errorOutput, errorOutputBuf)

        ExecResult result = execAction.execute()
        String standardOutput = standardOutputBuf.toString()
        String errorOutput = errorOutputBuf.toString()

        if (result.exitValue != 0) {
            throw new GradleException('SPIR-V compiler returned non-zero status')
        }

        errorOutput.lines().forEach { line ->
            if (line.contains('ERROR')) {
                throw new GradleException(line)
            }
        }

        standardOutput.lines().forEach { line ->
            if (line.contains('ERROR')) {
                throw new GradleException(line)
            }
        }
    }
}

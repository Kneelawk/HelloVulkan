package com.kneelawk.hellovulkan;

import org.lwjgl.system.MemoryStack;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Properties;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.VK10.*;

public class HelloVulkan {
	public static void main(String[] args) throws IOException {
//		System.getProperties().store(new FileOutputStream("system.properties"), null);

		glfwInit();

		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		long window = glfwCreateWindow(1280, 720, "Hello Vulkan", 0, 0);

		int extensionCount = 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer buf = stack.ints(extensionCount);
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, buf, null);
			extensionCount = buf.get(0);
		}

		System.out.println(extensionCount + " extensions supported");

		while (!glfwWindowShouldClose(window)) {
			glfwPollEvents();
		}

		glfwDestroyWindow(window);
		glfwTerminate();
	}
}

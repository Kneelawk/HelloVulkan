package com.kneelawk.hellovulkan;

import com.google.common.collect.Lists;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;

public class HelloVulkanApplication {
	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("com.kneelawk.hellovulkan.Debug", "true"));
	private static final int WINDOW_WIDTH = 1280;
	private static final int WINDOW_HEIGHT = 720;
	private static final String[] LAYERS = {
			"VK_LAYER_LUNARG_standard_validation"
	};
	private static final String[] DEBUG_EXTENSIONS = {
			VK_EXT_DEBUG_UTILS_EXTENSION_NAME
	};

	private long window;

	private VkInstance instance;
	private long debugUtilsMessenger;

	public void run() {
		initWindow();
		initVulkan();
		mainLoop();
		cleanup();
	}

	private void initWindow() {
		glfwInit();
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

		window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Hello Vulkan", NULL, NULL);
	}

	private void initVulkan() {
		checkExtensions();

		if (DEBUG) {
			checkLayers();
		}

		createInstance();
	}

	private void checkExtensions() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer extensionCountBuffer = stack.callocInt(1);
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCountBuffer, null);

			VkExtensionProperties.Buffer extensionPropertiesBuffer = VkExtensionProperties.malloc(extensionCountBuffer.get(0));
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCountBuffer, extensionPropertiesBuffer);

			List<String> requiredExtensions = getRequiredExtensions();

			System.out.println("Required Extensions: " + requiredExtensions);

			System.out.println(extensionCountBuffer.get(0) + " extensions found:");
			for (VkExtensionProperties extensionProperties : extensionPropertiesBuffer) {
				System.out.println("\t" + extensionProperties.extensionNameString() + " v" + extensionProperties.specVersion());
				requiredExtensions.remove(extensionProperties.extensionNameString());
			}

			if (requiredExtensions.size() > 0) {
				throw new RuntimeException("Missing required extensions: " + requiredExtensions);
			}
		}
	}

	private List<String> getRequiredExtensions() {
		List<String> requiredExtensions = Lists.newArrayList();
		PointerBuffer requiredExtensionsBuffer = glfwGetRequiredInstanceExtensions();
		for (int i = 0; i < requiredExtensionsBuffer.remaining(); i++) {
			requiredExtensions.add(requiredExtensionsBuffer.getStringASCII(i));
		}

		if (DEBUG) {
			requiredExtensions.addAll(Arrays.asList(DEBUG_EXTENSIONS));
		}

		return requiredExtensions;
	}

	private void checkLayers() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer layerCountBuffer = stack.callocInt(1);
			vkEnumerateInstanceLayerProperties(layerCountBuffer, null);

			VkLayerProperties.Buffer layerPropertiesBuffer = VkLayerProperties.malloc(layerCountBuffer.get(0));
			vkEnumerateInstanceLayerProperties(layerCountBuffer, layerPropertiesBuffer);

			List<String> requiredLayers = Lists.newArrayList(LAYERS);

			System.out.println("Required Layers: " + requiredLayers);

			System.out.println(layerCountBuffer.get(0) + " layers found:");
			for (VkLayerProperties layerProperties : layerPropertiesBuffer) {
				System.out.println("\t" + layerProperties.layerNameString() + " v" + layerProperties.specVersion());
				requiredLayers.remove(layerProperties.layerNameString());
			}

			if (requiredLayers.size() > 0) {
				throw new RuntimeException("Missing required layers: " + requiredLayers);
			}
		}
	}

	private void createInstance() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkApplicationInfo applicationInfo = VkApplicationInfo.callocStack(stack);
			applicationInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
			applicationInfo.pApplicationName(stack.ASCII("Hello Vulkan"));
			applicationInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
			applicationInfo.pEngineName(stack.ASCII("No Engine"));
			applicationInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
			applicationInfo.apiVersion(VK_API_VERSION_1_0);

			VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.callocStack(stack);
			instanceCreateInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
			instanceCreateInfo.pApplicationInfo(applicationInfo);

			List<String> extensions = getRequiredExtensions();
			PointerBuffer extensionsBuffer = stack.mallocPointer(extensions.size());
			for (int i = 0; i < extensions.size(); i++) {
				extensionsBuffer.put(i, stack.ASCII(extensions.get(i)));
			}

			instanceCreateInfo.ppEnabledExtensionNames(extensionsBuffer);

			if (DEBUG) {
				PointerBuffer layersBuffer = stack.mallocPointer(LAYERS.length);
				for (int i = 0; i < LAYERS.length; i++) {
					layersBuffer.put(i, stack.ASCII(LAYERS[i]));
				}

				instanceCreateInfo.ppEnabledLayerNames(layersBuffer);
			}

			PointerBuffer instanceBuf = stack.mallocPointer(1);
			if (vkCreateInstance(instanceCreateInfo, null, instanceBuf) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create VkInstance");
			}

			instance = new VkInstance(instanceBuf.get(0), instanceCreateInfo);

			if (DEBUG) {
				VkDebugUtilsMessengerCreateInfoEXT messengerCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
				messengerCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
				messengerCreateInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
						| VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
				messengerCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
						| VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
				messengerCreateInfo.pfnUserCallback(this::debugMessageCallback);
				messengerCreateInfo.pUserData(NULL);

				LongBuffer debugUtilsMessengerBuffer = stack.mallocLong(1);
				vkCreateDebugUtilsMessengerEXT(instance, messengerCreateInfo, null, debugUtilsMessengerBuffer);
				debugUtilsMessenger = debugUtilsMessengerBuffer.get(0);
			}
		}
	}

	private int debugMessageCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {
		String severity = "";
		switch (messageSeverity) {
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT:
				severity = "VERB";
				break;
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT:
				severity = "INFO";
				break;
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT:
				severity = "WARN";
				break;
			case VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT:
				severity = "ERROR";
				break;
		}

		String type = "";
		switch (messageType) {
			case VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT:
				type = "GENERAL";
				break;
			case VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT:
				type = "VALIDATION";
				break;
			case VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT:
				type = "PERFORMANCE";
				break;
		}

		VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

		System.out.printf("[%s][%s] %s\n", severity, type, callbackData.pMessageString());

		return VK_FALSE;
	}

	private void mainLoop() {
		while (!glfwWindowShouldClose(window)) {
			glfwPollEvents();
		}
	}

	private void cleanup() {
		if (DEBUG) {
			vkDestroyDebugUtilsMessengerEXT(instance, debugUtilsMessenger, null);
		}

		vkDestroyInstance(instance, null);

		glfwDestroyWindow(window);
		glfwTerminate();
	}
}

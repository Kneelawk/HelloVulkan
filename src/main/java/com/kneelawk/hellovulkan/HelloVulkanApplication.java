package com.kneelawk.hellovulkan;

import com.google.common.collect.Lists;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
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

	// GLFW stuff
	private long window;

	// Vulkan stuff
	private VkInstance instance;
	private long debugUtilsMessenger;
	private VkPhysicalDevice physicalDevice;
	private VkDevice device;
	private VkQueue graphicsQueue;

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
		pickPhysicalDevice();
		createLogicalDevice();
	}

	private void checkExtensions() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer extensionCountBuffer = stack.callocInt(1);
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCountBuffer, null);

			VkExtensionProperties.Buffer extensionPropertiesBuffer = VkExtensionProperties.mallocStack(extensionCountBuffer.get(0), stack);
			vkEnumerateInstanceExtensionProperties((ByteBuffer) null, extensionCountBuffer, extensionPropertiesBuffer);

			List<String> requiredExtensions = getRequiredExtensions();

			System.out.println("Required Extensions: " + requiredExtensions);

			System.out.println(extensionCountBuffer.get(0) + " extensions found:");
			for (VkExtensionProperties extensionProperties : extensionPropertiesBuffer) {
				int specVersion = extensionProperties.specVersion();
				System.out.println("\t" + extensionProperties.extensionNameString() + " v"
						+ VK_VERSION_MAJOR(specVersion) + "." + VK_VERSION_MINOR(specVersion) + "." + VK_VERSION_PATCH(specVersion));
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

			VkLayerProperties.Buffer layerPropertiesBuffer = VkLayerProperties.mallocStack(layerCountBuffer.get(0), stack);
			vkEnumerateInstanceLayerProperties(layerCountBuffer, layerPropertiesBuffer);

			List<String> requiredLayers = Lists.newArrayList(LAYERS);

			System.out.println("Required Layers: " + requiredLayers);

			System.out.println(layerCountBuffer.get(0) + " layers found:");
			for (VkLayerProperties layerProperties : layerPropertiesBuffer) {
				int specVersion = layerProperties.specVersion();
				System.out.println("\t" + layerProperties.layerNameString() + " v"
						+ VK_VERSION_MAJOR(specVersion) + "." + VK_VERSION_MINOR(specVersion) + "." + VK_VERSION_PATCH(specVersion));
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
				if (vkCreateDebugUtilsMessengerEXT(instance, messengerCreateInfo, null, debugUtilsMessengerBuffer) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create debug messenger");
				}
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

	private void pickPhysicalDevice() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer physicalDeviceCountBuffer = stack.callocInt(1);

			vkEnumeratePhysicalDevices(instance, physicalDeviceCountBuffer, null);

			int physicalDeviceCount = physicalDeviceCountBuffer.get(0);

			if (physicalDeviceCount == 0) {
				throw new RuntimeException("No physical device found that supports vulkan");
			}

			PointerBuffer physicalDeviceBuffer = stack.mallocPointer(physicalDeviceCount);

			vkEnumeratePhysicalDevices(instance, physicalDeviceCountBuffer, physicalDeviceBuffer);

			System.out.println(physicalDeviceCount + " physical devices:");
			for (int i = 0; i < physicalDeviceCount; i++) {
				VkPhysicalDevice dev = new VkPhysicalDevice(physicalDeviceBuffer.get(i), instance);
				printPhysicalDevice(dev);
				if (checkPhysicalDeviceCompatibility(dev) && physicalDevice == null) {
					physicalDevice = dev;
				}
			}

			if (physicalDevice == null) {
				throw new RuntimeException("No compatible physical device detected");
			}
		}
	}

	private void printPhysicalDevice(VkPhysicalDevice physicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceProperties physicalDeviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);

			vkGetPhysicalDeviceProperties(physicalDevice, physicalDeviceProperties);

			int apiVersion = physicalDeviceProperties.apiVersion();
			int driverVersion = physicalDeviceProperties.driverVersion();
			System.out.println("\t" + physicalDeviceProperties.deviceNameString() + " - API: "
					+ VK_VERSION_MAJOR(apiVersion) + "." + VK_VERSION_MINOR(apiVersion) + "." + VK_VERSION_PATCH(apiVersion)
					+ " & DRIVER: " + VK_VERSION_MAJOR(driverVersion) + "." + VK_VERSION_MINOR(driverVersion) + "."
					+ VK_VERSION_PATCH(driverVersion));

			IntBuffer queueFamilyCountBuffer = stack.callocInt(1);
			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, null);
			int queueFamilyCount = queueFamilyCountBuffer.get(0);

			VkQueueFamilyProperties.Buffer queueFamilyPropertiesBuffer = VkQueueFamilyProperties.mallocStack(queueFamilyCount, stack);
			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, queueFamilyPropertiesBuffer);

			System.out.println("\t" + queueFamilyCount + " queue families:");
			for (int i = 0; i < queueFamilyCount; i++) {
				VkQueueFamilyProperties queueFamilyProperties = queueFamilyPropertiesBuffer.get(i);

				List<String> queueFamilyFlags = Lists.newArrayList();
				if ((queueFamilyProperties.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
					queueFamilyFlags.add("VK_QUEUE_GRAPHICS_BIT");
				}
				if ((queueFamilyProperties.queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0) {
					queueFamilyFlags.add("VK_QUEUE_COMPUTE_BIT");
				}
				if ((queueFamilyProperties.queueFlags() & VK_QUEUE_TRANSFER_BIT) != 0) {
					queueFamilyFlags.add("VK_QUEUE_TRANSFER_BIT");
				}
				if ((queueFamilyProperties.queueFlags() & VK_QUEUE_SPARSE_BINDING_BIT) != 0) {
					queueFamilyFlags.add("VK_QUEUE_SPARSE_BINDING_BIT");
				}

				System.out.println("\t\tCount: " + queueFamilyProperties.queueCount() + ", Flags: " + queueFamilyFlags);
			}
		}
	}

	private boolean checkPhysicalDeviceCompatibility(VkPhysicalDevice physicalDevice) {
		QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

		return indices.isComplete();
	}

	private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice physicalDevice) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			QueueFamilyIndices indices = new QueueFamilyIndices();

			IntBuffer queueFamilyCountBuffer = stack.callocInt(1);
			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, null);
			int queueFamilyCount = queueFamilyCountBuffer.get(0);

			VkQueueFamilyProperties.Buffer queueFamilyPropertiesBuffer = VkQueueFamilyProperties.mallocStack(queueFamilyCount, stack);
			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCountBuffer, queueFamilyPropertiesBuffer);

			for (int i = 0; i < queueFamilyCount; i++) {
				VkQueueFamilyProperties queueFamilyProperties = queueFamilyPropertiesBuffer.get(i);
				if (queueFamilyProperties.queueCount() > 0 && (queueFamilyProperties.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
					indices.setGraphicsFamily(i);
				}

				if (indices.isComplete()) {
					break;
				}
			}

			return indices;
		}
	}

	private void createLogicalDevice() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

			VkDeviceQueueCreateInfo queueCreateInfo = VkDeviceQueueCreateInfo.callocStack(stack);
			queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
			queueCreateInfo.queueFamilyIndex(indices.getGraphicsFamily());
			queueCreateInfo.pQueuePriorities(stack.floats(1.0f));

			VkPhysicalDeviceFeatures physicalDeviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);

			VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack);
			deviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
			deviceCreateInfo.pQueueCreateInfos(VkDeviceQueueCreateInfo.mallocStack(1, stack).put(0, queueCreateInfo));
			deviceCreateInfo.pEnabledFeatures(physicalDeviceFeatures);

			// shouldn't be necessary with up-to-date drivers, but older drivers need this
			if (DEBUG) {
				PointerBuffer layersBuffer = stack.mallocPointer(LAYERS.length);
				for (int i = 0; i < LAYERS.length; i++) {
					layersBuffer.put(i, stack.ASCII(LAYERS[i]));
				}

				deviceCreateInfo.ppEnabledLayerNames(layersBuffer);
			}

			PointerBuffer deviceBuffer = stack.mallocPointer(1);
			if (vkCreateDevice(physicalDevice, deviceCreateInfo, null, deviceBuffer) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create the logical device");
			}

			device = new VkDevice(deviceBuffer.get(0), physicalDevice, deviceCreateInfo);

			PointerBuffer graphicsQueueBuffer = stack.mallocPointer(1);
			vkGetDeviceQueue(device, indices.getGraphicsFamily(), 0, graphicsQueueBuffer);
			graphicsQueue = new VkQueue(graphicsQueueBuffer.get(), device);
		}
	}

	private void mainLoop() {
		while (!glfwWindowShouldClose(window)) {
			glfwPollEvents();
		}
	}

	private void cleanup() {
		vkDestroyDevice(device, null);

		if (DEBUG) {
			vkDestroyDebugUtilsMessengerEXT(instance, debugUtilsMessenger, null);
		}

		vkDestroyInstance(instance, null);

		glfwDestroyWindow(window);
		glfwTerminate();
	}

	private static class QueueFamilyIndices {
		private int graphicsFamily;
		private boolean hasGraphicsFamily = false;

		public void setGraphicsFamily(int graphicsFamily) {
			this.graphicsFamily = graphicsFamily;
			hasGraphicsFamily = true;
		}

		public int getGraphicsFamily() {
			return graphicsFamily;
		}

		public boolean isComplete() {
			return hasGraphicsFamily;
		}
	}
}

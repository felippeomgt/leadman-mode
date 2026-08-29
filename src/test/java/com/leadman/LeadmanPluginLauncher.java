package com.leadman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts a real RuneLite client with Leadman loaded as a built-in plugin.
 *
 * <p>This is the standard way to test an external plugin: there is no sideloading path
 * for a jar, so the client is launched from the dependency with the plugin registered
 * before startup.
 *
 * <pre>
 *   gradle run
 * </pre>
 *
 * or run this class directly from an IDE. Lives in the test source set so it is never
 * shipped in the plugin jar.
 */
public class LeadmanPluginLauncher
{
	// loadBuiltin takes a generic varargs array, which javac cannot prove safe at the
	// call site. One element, one known type, so it is.
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LeadmanPlugin.class);
		RuneLite.main(args);
	}
}

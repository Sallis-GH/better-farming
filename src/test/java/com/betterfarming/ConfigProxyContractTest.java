package com.betterfarming;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * RuneLite serves config interfaces through a dynamic proxy
 * (ConfigInvocationHandler) that intercepts EVERY non-static default method
 * — and returns null for ones without an @ConfigItem annotation, which NPEs
 * on primitive unboxing at the call site. This shipped once: a default
 * showType(PatchType) dispatch helper crashed panel construction in the real
 * client while headless tests (which call default methods directly on an
 * anonymous class) stayed green. Helpers on the interface must be static;
 * this test enforces the contract.
 */
public class ConfigProxyContractTest
{
	@Test
	public void everyNonStaticDefaultMethodCarriesConfigItem()
	{
		for (Method m : BetterFarmingConfig.class.getDeclaredMethods())
		{
			if (Modifier.isStatic(m.getModifiers()) || !m.isDefault())
			{
				continue;
			}
			assertTrue("Default method " + m.getName() + " has no @ConfigItem: the RuneLite "
					+ "config proxy returns null for it. Make it static or annotate it.",
				m.isAnnotationPresent(ConfigItem.class));
		}
	}

	/**
	 * Belt and braces: drive the panel-facing dispatch through a proxy that
	 * behaves like RuneLite's (null for un-annotated methods, default value
	 * for annotated ones) — the exact scenario that crashed in the client.
	 */
	@Test
	public void showTypeDispatchSurvivesTheConfigProxy() throws Exception
	{
		InvocationHandler runeliteLike = (Object proxy, Method method, Object[] args) ->
		{
			if (!method.isAnnotationPresent(ConfigItem.class))
			{
				return null; // what ConfigInvocationHandler does (after a WARN)
			}
			// Annotated defaults resolve to their interface default value.
			return java.lang.invoke.MethodHandles.lookup()
				.findSpecial(BetterFarmingConfig.class, method.getName(),
					java.lang.invoke.MethodType.methodType(
						method.getReturnType(), method.getParameterTypes()),
					BetterFarmingConfig.class)
				.bindTo((BetterFarmingConfig) proxy)
				.invokeWithArguments(args == null ? new Object[0] : args);
		};
		BetterFarmingConfig proxied = (BetterFarmingConfig) Proxy.newProxyInstance(
			BetterFarmingConfig.class.getClassLoader(),
			new Class<?>[]{BetterFarmingConfig.class}, runeliteLike);

		try
		{
			for (com.betterfarming.data.PatchType type : com.betterfarming.data.PatchType.values())
			{
				assertTrue("all types default to shown",
					BetterFarmingConfig.showType(proxied, type));
			}
		}
		catch (NullPointerException ex)
		{
			fail("showType dispatch hit the proxy null path: " + ex);
		}
	}
}

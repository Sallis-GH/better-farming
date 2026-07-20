package com.betterfarming.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocationsTest
{
	@Test
	public void navigationalPrefixesAreStripped()
	{
		assertEquals("Port Phasmatys", Locations.display("West of Port Phasmatys"));
		assertEquals("Ardougne", Locations.display("North of Ardougne"));
		assertEquals("Falador", Locations.display("South of Falador"));
		assertEquals("Catherby", Locations.display("East of Catherby"));
		assertEquals("Hosidius", Locations.display("South-west of Hosidius"));
		assertEquals("Lumbridge farm", Locations.display("North of Lumbridge farm"));
	}

	@Test
	public void bareAndCompassOnlyNamesPassThrough()
	{
		assertEquals("Falador Park", Locations.display("Falador Park"));
		assertEquals("Farming Guild (cave)", Locations.display("Farming Guild (cave)"));
		// No "of": stripping these would collide the two Etceteria groups.
		assertEquals("South-east Etceteria", Locations.display("South-east Etceteria"));
		assertEquals("South-west Etceteria", Locations.display("South-west Etceteria"));
		// "of" mid-name is not a prefix.
		assertEquals("Tree Gnome Stronghold", Locations.display("Tree Gnome Stronghold"));
	}
}

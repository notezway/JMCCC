package com.github.to2mbn.jyal;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PropertiesGameProfile extends GameProfile {

	private Map<String, String> properties;

	public PropertiesGameProfile(UUID uuid, String name, Map<String, String> properties) {
		super(uuid, name);
		this.properties = properties;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), properties);
	}

	@Override
	public boolean equals(Object obj) {
		if (super.equals(obj) && obj instanceof PropertiesGameProfile) {
			PropertiesGameProfile another = (PropertiesGameProfile) obj;
			return Objects.equals(properties, another.properties);
		}
		return false;
	}

	@Override
	public String toString() {
		return "PropertiesGameProfile [properties=" + properties + ", uuid=" + getUUID() + ", name=" + getName() + "]";
	}

}

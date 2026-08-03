package io.jmmym.bedwarspro.scoreboard.config;

public enum EnumLocale {
	ZH_CN("zh_CN");

	private String name;

	private EnumLocale(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public static EnumLocale getByName(String n) {
		for (EnumLocale type : values()) {
			if (type.getName().equals(n)) {
				return type;
			}
		}
		return ZH_CN;
	}
}

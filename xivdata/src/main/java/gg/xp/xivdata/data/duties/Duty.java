package gg.xp.xivdata.data.duties;

import java.util.List;

public interface Duty {
	String getName();

	Expansion getExpac();

	DutyType getType();

	List<Long> getZoneIds();
}

package gg.xp.xivsupport.persistence.gui;

import gg.xp.xivsupport.gui.WrapLayout;
import gg.xp.xivsupport.gui.tables.filters.TextFieldWithValidation;
import gg.xp.xivsupport.persistence.settings.IntSetting;

import javax.swing.*;
import java.awt.*;

public class IntSettingGui {

	private final TextFieldWithValidation<Integer> checkBox;
	private final String label;
	private JLabel jLabel;

	public IntSettingGui(IntSetting setting, String label) {
		checkBox = new TextFieldWithValidation<>(Integer::parseInt, setting::set, Long.toString(setting.get()));
		this.label = label;
	}

	public Component getTextBoxOnly() {
		return checkBox;
	}

	public Component getLabelOnly() {
		if (jLabel == null) {
			jLabel = new JLabel(label);
			jLabel.setLabelFor(checkBox);
		}
		return jLabel;
	}

	public JPanel getComponent() {
		JPanel box = new JPanel();
		box.setLayout(new WrapLayout());
		box.add(getTextBoxOnly());
		box.add(getLabelOnly());
		box.setMaximumSize(box.getPreferredSize());
		return box;
	}
}

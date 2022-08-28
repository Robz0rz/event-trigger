package gg.xp.xivsupport.callouts.gui;

import gg.xp.xivsupport.callouts.ModifiedCalloutHandle;
import gg.xp.xivsupport.callouts.audio.SoundFilesManager;
import gg.xp.xivsupport.persistence.gui.BooleanSettingGui;
import gg.xp.xivsupport.persistence.gui.ColorSettingGui;
import gg.xp.xivsupport.persistence.gui.StringSettingGui;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.persistence.settings.ColorSetting;
import gg.xp.xivsupport.persistence.settings.StringSetting;

import javax.swing.*;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ActionListener;

public class CalloutSettingGui {

	private final JCheckBox callCheckbox;
	private final JPanel ttsPanel;
	private final JPanel textPanel;
	private final JPanel soundPanel;
	private final JCheckBox ttsCheckbox;
	private final JTextField ttsTextBox;
	private final JCheckBox textCheckbox;
	private final JCheckBox sameCheckBox;
	private final JTextField textTextBox;
	private final BooleanSetting allText;
	private final BooleanSetting allTts;
	private final JPanel colorPickerPanel;
	private final JButton colorPicker;
	private boolean enabledByParent = true;

	public CalloutSettingGui(ModifiedCalloutHandle call, SoundFilesManager soundMgr) {
		callCheckbox = new BooleanSettingGui(call.getEnable(), call.getDescription(), () -> enabledByParent).getComponent();
		BooleanSetting enableTts = call.getEnableTts();
		StringSetting ttsSetting = call.getTtsSetting();
		BooleanSetting enableText = call.getEnableText();
		BooleanSetting sameText = call.getSameText();
		StringSetting textSetting = call.getTextSetting();
		ColorSetting colorOverride = call.getTextColorOverride();
		this.allText = call.getAllTextEnabled();
		this.allTts = call.getAllTtsEnabled();

		{
			ttsPanel = new JPanel() {
				@Override
				public boolean isEnabled() {
					return CalloutSettingGui.this.isThisCallEnabled() && allTts.get();
				}
			};
			ttsPanel.setLayout(new BoxLayout(ttsPanel, BoxLayout.LINE_AXIS));
			ttsCheckbox = new BooleanSettingGui(enableTts, "TTS:", ttsPanel::isEnabled).getComponent();
			ttsCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
			ttsPanel.add(ttsCheckbox);
			ttsTextBox = new StringSettingGui(ttsSetting, null, () -> ttsPanel.isEnabled() && ttsCheckbox.isSelected()).getTextBoxOnly();
			ttsPanel.add(ttsTextBox);
		}
		{
			textPanel = new JPanel() {
				@Override
				public boolean isEnabled() {
					return CalloutSettingGui.this.isThisCallEnabled() && allText.get();
				}
			};
			textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.LINE_AXIS));

			textCheckbox = new BooleanSettingGui(enableText, "Text:", textPanel::isEnabled).getComponent();
			textCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
			textPanel.add(textCheckbox);

			sameCheckBox = new BooleanSettingGui(sameText, "Same as TTS:", () -> textPanel.isEnabled() && textCheckbox.isSelected()).getComponent();
			sameCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
			textPanel.add(sameCheckBox);

			textTextBox = new StringSettingGui(textSetting, null, () -> textPanel.isEnabled() && textCheckbox.isSelected() && !sameCheckBox.isSelected()).getTextBoxOnly();
			textPanel.add(textTextBox);
		}
		{
			soundPanel = new JPanel();
			soundPanel.setLayout(new BoxLayout(soundPanel, BoxLayout.LINE_AXIS));
			JLabel soundLabel = new JLabel("Sound: ");

			JComboBox<String> filePicker = new JComboBox<>(new ComboBoxModel<>() {
				@Override
				public void setSelectedItem(Object item) {
					if (item.equals("None")) {
						item = "";
					}
					call.getSoundSetting().set(item.toString());
				}

				@Override
				public Object getSelectedItem() {
					String s = call.getSoundSetting().get();
					if (s.isEmpty()) {
						return "None";
					}
					return s;
				}

				@Override
				public int getSize() {
					return soundMgr.getSoundFilesAndNone().size();
				}

				@Override
				public String getElementAt(int index) {
					return soundMgr.getSoundFilesAndNone().get(index);
				}

				@Override
				public void addListDataListener(ListDataListener l) {

				}

				@Override
				public void removeListDataListener(ListDataListener l) {

				}
			});
			soundPanel.add(soundLabel);
			soundPanel.add(filePicker);
		}
		{
			colorPicker = new ColorSettingGui(colorOverride, "Text Color", textPanel::isEnabled).getButtonOnly();
			colorPicker.setPreferredSize(new Dimension(20, 20));
			colorPicker.setMinimumSize(new Dimension(20, 20));
			colorPicker.setMaximumSize(new Dimension(50, 256));
			colorPickerPanel = new JPanel();
			colorPickerPanel.setLayout(new BoxLayout(colorPickerPanel, BoxLayout.LINE_AXIS));

			colorPickerPanel.add(new JLabel("Override Text Color: "));
			colorPickerPanel.add(colorPicker);

//			colorPickerPanel.setPreferredSize(new Dimension(20, 10));
		}
		recalcEnabledDisabledStatus();
		ActionListener l = e -> recalcEnabledDisabledStatus();
		callCheckbox.addActionListener(l);
		ttsCheckbox.addActionListener(l);
		textCheckbox.addActionListener(l);
		sameCheckBox.addActionListener(l);
		allText.addListener(this::recalcEnabledDisabledStatus);
		allTts.addListener(this::recalcEnabledDisabledStatus);
	}

	private boolean isThisCallEnabled() {
		return callCheckbox.isSelected() && enabledByParent;
	}

	private void recalcEnabledDisabledStatus() {
//		SwingUtilities.invokeLater(() -> {
//			ttsPanel.setVisible(false);
//			ttsPanel.setVisible(true);
//			textPanel.setVisible(false);
//			textPanel.setVisible(true);
//		});
		callCheckbox.repaint();
		ttsPanel.updateUI();
		textPanel.updateUI();
	}

	public void setEnabledByParent(boolean enabledByParent) {
		this.enabledByParent = enabledByParent;
		recalcEnabledDisabledStatus();
	}

	public JCheckBox getCallCheckbox() {
		return callCheckbox;
	}

	public JPanel getTtsPanel() {
		return ttsPanel;
	}

	public JPanel getTextPanel() {
		return textPanel;
	}

	public JPanel getSoundPanel() {
		return soundPanel;
	}

	public Component getColorPicker() {
		return colorPicker;
	}

	public JPanel getColorPickerPanel() {
		return colorPickerPanel;
	}

	public void setVisible(boolean visible) {
//		private final JCheckBox callCheckbox;
//		private final JPanel ttsPanel;
//		private final JPanel textPanel;
//		private final JCheckBox ttsCheckbox;
//		private final JTextField ttsTextBox;
//		private final JCheckBox textCheckbox;
//		private final JCheckBox sameCheckBox;
//		private final JTextField textTextBox;
		callCheckbox.setVisible(visible);
		ttsPanel.setVisible(visible);
		textPanel.setVisible(visible);
		colorPickerPanel.setVisible(visible);
		soundPanel.setVisible(visible);

	}
}

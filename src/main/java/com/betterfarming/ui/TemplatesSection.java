package com.betterfarming.ui;

import com.betterfarming.state.PatchSelectionService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Save/load named run templates — a template is a snapshot of the active
 * patch groups and seed selections, so switching between a herb run, a tree
 * run and a full rotation is one Load instead of re-clicking every card.
 * Loading fires the normal selection events, so cards, run items and the
 * route all update as if clicked by hand. All work happens on the EDT.
 */
public class TemplatesSection extends JPanel
{
	private static final Color COLOR_MUTED = new Color(0x88, 0x88, 0x88);

	private final PatchSelectionService selectionService;
	private final JComboBox<String> templateDropdown;
	private final JButton loadButton;
	private final JButton deleteButton;

	public TemplatesSection(PatchSelectionService selectionService)
	{
		this.selectionService = selectionService;

		setLayout(new BorderLayout());
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("Templates");
		header.setName("section-header:TEMPLATES");
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
		header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 16));
		header.setOpaque(true);
		header.setBackground(new Color(0x26, 0x26, 0x26));

		JPanel body = new JPanel();
		body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
		body.setOpaque(true);
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 14));

		templateDropdown = new JComboBox<>();
		templateDropdown.setName("template-dropdown");
		templateDropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
		templateDropdown.setRenderer(new javax.swing.plaf.basic.BasicComboBoxRenderer()
		{
			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> list,
				Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value == null)
				{
					setText("No templates saved");
					setForeground(COLOR_MUTED);
				}
				return this;
			}
		});
		templateDropdown.addActionListener(e -> syncButtons());
		body.add(templateDropdown);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		buttons.setOpaque(false);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

		loadButton = new JButton("Load");
		loadButton.setName("template-load");
		loadButton.setFont(loadButton.getFont().deriveFont(11f));
		loadButton.setToolTipText("Replace the current setup with this template");
		loadButton.addActionListener(e -> onLoad());
		buttons.add(loadButton);

		JButton saveButton = new JButton("Save…");
		saveButton.setName("template-save");
		saveButton.setFont(saveButton.getFont().deriveFont(11f));
		saveButton.setToolTipText("Save the current active patches and seeds as a template");
		saveButton.addActionListener(e -> onSave());
		buttons.add(saveButton);

		deleteButton = new JButton("Delete");
		deleteButton.setName("template-delete");
		deleteButton.setFont(deleteButton.getFont().deriveFont(11f));
		deleteButton.addActionListener(e -> onDelete());
		buttons.add(deleteButton);

		body.add(buttons);

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		refreshDropdown(null);
	}

	private void onLoad()
	{
		String name = (String) templateDropdown.getSelectedItem();
		if (name != null)
		{
			selectionService.loadTemplate(name);
		}
	}

	private void onSave()
	{
		String suggestion = (String) templateDropdown.getSelectedItem();
		String name = (String) JOptionPane.showInputDialog(this,
			"Template name:", "Save template", JOptionPane.PLAIN_MESSAGE,
			null, null, suggestion == null ? "" : suggestion);
		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		selectionService.saveTemplate(name.trim());
		refreshDropdown(name.trim());
	}

	private void onDelete()
	{
		String name = (String) templateDropdown.getSelectedItem();
		if (name == null)
		{
			return;
		}
		selectionService.deleteTemplate(name);
		refreshDropdown(null);
	}

	private void refreshDropdown(String select)
	{
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		for (String name : selectionService.templateNames())
		{
			model.addElement(name);
		}
		templateDropdown.setModel(model);
		if (select != null)
		{
			templateDropdown.setSelectedItem(select);
		}
		syncButtons();
	}

	private void syncButtons()
	{
		boolean hasSelection = templateDropdown.getSelectedItem() != null;
		loadButton.setEnabled(hasSelection);
		deleteButton.setEnabled(hasSelection);
	}
}

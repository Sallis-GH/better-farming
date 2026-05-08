package com.betterfarming.ui;

import com.betterfarming.data.PatchType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A collapsible group of PatchCards for a single PatchType. Header shows
 * "Allotment (17) ▼"; clicking toggles cards visibility. Default state:
 * expanded. Collapse state lives in-memory only (not persisted).
 */
public class PatchTypeSection extends JPanel
{
	private final JLabel headerLabel;
	private final JPanel cardsContainer;
	private boolean expanded = true;

	public PatchTypeSection(PatchType type, List<? extends javax.swing.JComponent> cards)
	{
		setLayout(new BorderLayout());
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		headerLabel = new JLabel();
		headerLabel.setName("section-header:" + type.name());
		headerLabel.setForeground(Color.WHITE);
		headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
		headerLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 16));
		headerLabel.setOpaque(true);
		headerLabel.setBackground(new Color(0x26, 0x26, 0x26));
		headerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		headerLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleExpanded();
			}
		});

		cardsContainer = new JPanel();
		cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));
		cardsContainer.setOpaque(false);
		for (javax.swing.JComponent card : cards)
		{
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			cardsContainer.add(card);
		}

		add(headerLabel, BorderLayout.NORTH);
		add(cardsContainer, BorderLayout.CENTER);

		renderHeader(type, cards.size());
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		cardsContainer.setVisible(expanded);
		// Update caret
		String text = headerLabel.getText();
		if (expanded)
		{
			headerLabel.setText(text.replace("▶", "▼"));
		}
		else
		{
			headerLabel.setText(text.replace("▼", "▶"));
		}
		revalidate();
		repaint();
	}

	private void renderHeader(PatchType type, int count)
	{
		// "Allotment (17) ▼" — reformat enum name to title case
		String typeLabel = humanize(type);
		headerLabel.setText("<html>" + typeLabel
			+ " <font color='#888888'>(" + count + ")</font>"
			+ "  <font color='#888888'>▼</font></html>");
	}

	static String humanize(PatchType type)
	{
		StringBuilder sb = new StringBuilder();
		for (String word : type.name().toLowerCase().split("_"))
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(word.charAt(0)))
			  .append(word.substring(1));
		}
		return sb.toString();
	}
}

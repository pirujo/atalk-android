package net.java.sip.communicator.plugin.desktoputil;

import javax.swing.text.JTextComponent;
import java.awt.Color;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

public class SIPCommTextField extends JTextField implements DocumentListener {

	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The default text.
	 */
	private String defaultText;

	/**
	 * Indicates if the default text is currently visible.
	 */
	private boolean isDefaultTextVisible;
	private Color foregroundColor = Color.BLACK;
	private Color defaultTextColor = Color.GRAY;


	public SIPCommTextField(String text) {
		super(text);
		if (text != null && text.length() > 0) {
			this.defaultText = text;
			isDefaultTextVisible = true;
		}

		JTextComponent.getDocument().addDocumentListener(this);
	}

	public String getText() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setBackground(Object green) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertUpdate(DocumentEvent paramDocumentEvent) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeUpdate(DocumentEvent paramDocumentEvent) {
		// TODO Auto-generated method stub

	}

	@Override
	public void changedUpdate(DocumentEvent paramDocumentEvent) {
		// TODO Auto-generated method stub

	}

	public Document getDocument() {
		// TODO Auto-generated method stub
		return null;
	}
}

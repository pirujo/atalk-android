package javax.swing;


import net.java.sip.communicator.plugin.desktoputil.SelectedObject;

import java.awt.Dimension;
import java.awt.event.ActionListener;
import javax.swing.event.EventListenerList;

public class JMenuItem implements Accessible {
	protected EventListenerList listenerList = new EventListenerList();

	public void setText(String i18nString) {
		// TODO Auto-generated method stub
		
	}

	public void setActionCommand(String actionCommandEndOtr) {
		// TODO Auto-generated method stub
		
	}

	public void setEnabled(boolean enableManual) {
		// TODO Auto-generated method stub
		
	}

	public void setIcon(ImageIcon image) {
		// TODO Auto-generated method stub
		
	}

	public void repaint() {
		// TODO Auto-generated method stub
		
	}
	
	
	public void setMininumSize(Dimension dimension) {
		// TODO Auto-generated method stub - cmeng
		
	}
	
	public void setMaximumSize(Dimension dimension) {
		// TODO Auto-generated method stub - cmeng
		
	}
	
	public void setPreferredSize(Dimension dimension) {
		// TODO Auto-generated method stub - cmeng
		
	}
	
	public void setBorder(Object object) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setOpaque(boolean b) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setVisible(boolean b) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public boolean isVisible() {
		// TODO Auto-generated method stub - cmeng
		return false;
	}

	public void setSelected(SelectedObject selectedObject) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setSelected(boolean paramBoolean) {
	}
	

	public Object getItem(int i) {
		// TODO Auto-generated method stub - cmeng
		return null;
	}

	public int getItemCount() {
		// TODO Auto-generated method stub - cmeng
		return 0;
	}


	public void addActionListener(ActionListener paramActionListener) {
		this.listenerList.add(ActionListener.class, paramActionListener);
	}	
}

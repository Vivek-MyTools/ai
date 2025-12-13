package com.generic.tools.ai;

import javax.swing.SwingUtilities;

public class AIAgentUILauncher {

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
            DesktopChatApp app = new DesktopChatApp();
            app.setVisible(true);
            System.out.println("🚀 Desktop App Started!");
        });
	}
} 

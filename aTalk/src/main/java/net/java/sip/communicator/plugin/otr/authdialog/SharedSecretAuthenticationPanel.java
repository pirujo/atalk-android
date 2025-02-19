/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr.authdialog;

import net.java.sip.communicator.plugin.desktoputil.TransparentPanel;
import net.java.sip.communicator.plugin.otr.OtrActivator;

import java.awt.*;
import javax.swing.*;


/**
 * @author Marin Dzhigarov
 */
@SuppressWarnings("serial")
public class SharedSecretAuthenticationPanel extends TransparentPanel
{
    /**
     * The text field where the authentication initiator will type his answer.
     */
    private final JTextField secret = new JTextField();

    /**
     * Creates an instance SecretQuestionAuthenticationPanel.
     */
    SharedSecretAuthenticationPanel()
    {
        initComponents();
    }

    /**
     * Initializes the {@link SecretQuestionAuthenticationPanel} components.
     */
    private void initComponents()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JTextArea generalInformation = new CustomTextArea();
        generalInformation.setText(OtrActivator.resourceService.getI18NString("plugin.otr.authbuddydialog.AUTH_BY_SECRET_INFO_INIT"));
        this.add(generalInformation);

        this.add(Box.createVerticalStrut(10));

        JPanel questionAnswerPanel = new JPanel(new GridBagLayout());
        questionAnswerPanel.setBorder(BorderFactory.createEtchedBorder());

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 0, 5);
        c.weightx = 1;

        JLabel questionLabel =
                new JLabel(OtrActivator.resourceService.getI18NString("plugin.otr.authbuddydialog.SHARED_SECRET"));
        questionAnswerPanel.add(questionLabel, c);

        c.gridy = 1;
        c.insets = new Insets(0, 5, 5, 5);
        questionAnswerPanel.add(secret, c);

        this.add(questionAnswerPanel);
        this.add(new Box.Filler(
                new Dimension(300, 150),
                new Dimension(300, 150),
                new Dimension(300, 150)));
    }

    /**
     * Returns the shared secret text.
     *
     * @return The shared secret text.
     */
    String getSecret()
    {
        return secret.getText();
    }
}

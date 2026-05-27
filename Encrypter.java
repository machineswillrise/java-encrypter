///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.slf4j:slf4j-api:2.0.18
//DEPS org.slf4j:slf4j-simple:2.0.18

import java.io.IOException;

import java.awt.Color;
import java.awt.Container;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;

import java.util.Arrays;
import java.util.Base64;

import java.util.function.Consumer;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Box;

import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Encrypter {
	private static final String TITLE = "AES Encrypter";
	private static final Logger logger = LoggerFactory.getLogger(Encrypter.class);

	private AESEngine aes;
	private ClipboardUtil clipboard;

	private JFrame frame;
	private JPanel contentPanel;

	private PlaceholderJTextField plainTextField;
	private PlaceholderJTextField keyField;
	private PlaceholderJTextField encryptedField;
	private SecretKey currentKey;

	public Encrypter() {
		prepareGui();
		try {
			aes = new AESEngine();
			clipboard = new ClipboardUtil(Toolkit.getDefaultToolkit().getSystemClipboard());
		} catch (NoSuchAlgorithmException e) {
			logger.error("This JVM does not support AES.");
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			Encrypter encrypter = new Encrypter();
			encrypter.showEncryptionDialog();
		});
	}

	private void prepareGui() {
		logger.info("Encrypter is loading...");

		frame = new JFrame(TITLE);
		frame.setSize(512, 512);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
				logger.info("Encrypter is closing...");
				System.exit(0);
			}
		});

		frame.add(contentPanel);
		logger.info("Encrypter has loaded.");
	}

	private void showEncryptionDialog() {
		JLabel title = new JLabel(TITLE);

		JButton pasteFromClipboardButton = button("Paste From Clipboard", e -> {
			try {
				plainTextField.setText(clipboard.pasteFromClipboard());
			} catch (UnsupportedFlavorException | IOException ex) {
				logger.error("Failed to paste from clipboard!");
			}
		});

		plainTextField = field("Text to encrypt");
		keyField = field("AES key (Base64)");
		encryptedField = field("Encrypted output...");

		JButton generateKeyButton = button("Generate Key", e -> {
			currentKey = aes.generateKey();
			keyField.setText(Base64.getEncoder().encodeToString(currentKey.getEncoded()));
			keyField.setForeground(Color.BLACK);
		});

		JButton encryptButton = button("Encrypt", e -> {
			try {
				currentKey = getKeyFromField();
				if (currentKey == null)
					return;

				String encrypted = aes.encrypt(plainTextField.getText(), currentKey);
				encryptedField.setText(encrypted);
				encryptedField.setForeground(Color.BLACK);
			} catch (Exception ex) {
				logger.error("Encryption failed", ex);
			}
		});

		JButton decryptButton = button("Decrypt", e -> {
			try {
				currentKey = getKeyFromField();
				if (currentKey == null)
					return;

				String decrypted = aes.decrypt(encryptedField.getText(), currentKey);
				plainTextField.setText(decrypted);
				plainTextField.setForeground(Color.BLACK);
			} catch (Exception ex) {
				logger.error("Decryption failed", ex);
			}
		});

		JButton copyToClipboardButton = button("Copy To Clipboard", e -> {
			String content = encryptedField.getText();
			boolean isEmpty = content.isEmpty();

			if (isEmpty)
				return;

			clipboard.copyToClipboard(content);
		});

		addAll(
			title,
			pasteFromClipboardButton,
			plainTextField,
			keyField,
			generateKeyButton,
			encryptButton,
			decryptButton,
			encryptedField,
			copyToClipboardButton
		);
	}

	private SecretKey getKeyFromField() {
		try {
			byte[] keyBytes = Base64.getDecoder().decode(keyField.getText().trim());
			return new SecretKeySpec(keyBytes, "AES");
		} catch (IllegalArgumentException e) {
			logger.error("Invalid base64 key", e);
			return null;
		}
	}

	private PlaceholderJTextField field(String placeholder) {
		PlaceholderJTextField field = new PlaceholderJTextField(placeholder);

		field.setMaximumSize(new Dimension(400, 30));
		field.setPreferredSize(new Dimension(400, 30));
		field.setAlignmentX(Component.CENTER_ALIGNMENT);

		return field;
	}

	private JButton button(String text, Consumer<?> action) {
		JButton button = new JButton(text);
		button.addActionListener(e -> action.accept(null));
		return button;
	}

	private void addAll(JComponent... components) {
		for (JComponent component : components) {
			component.setAlignmentX(Component.CENTER_ALIGNMENT);
			contentPanel.add(component);
			contentPanel.add(Box.createVerticalStrut(10));  /* Add spacer */
		}

		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
}

class ClipboardUtil {
	private Clipboard clipboard;

	public ClipboardUtil(Clipboard clipboard) {
		this.clipboard = clipboard;
	}

	public void copyToClipboard(String text) {
		StringSelection data = new StringSelection(text);
		clipboard.setContents(data, null);
	}

	public String pasteFromClipboard() throws UnsupportedFlavorException, IOException {
		Transferable transferable = clipboard.getContents(null);

		if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			String data = (String)transferable.getTransferData(DataFlavor.stringFlavor);
			return data;
		} else {
			return null;
		}
	}
}

class PlaceholderJTextField extends JTextField {
	public PlaceholderJTextField(String placeholder) {
		setText(placeholder);
		setForeground(Color.GRAY);

		addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				if (getText().equals(placeholder)) {
					setText("");
					setForeground(Color.BLACK);
				}
			}
		});

		addFocusListener(new FocusAdapter() {
			public void focusLost(FocusEvent e) {
				if (getText().isEmpty()) {
					setText(placeholder);
					setForeground(Color.GRAY);
				}
			}
		});
	}
}

class AESEngine {
	private static final Logger logger = LoggerFactory.getLogger(AESEngine.class);

	private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;    /* GCM recommended */
	private static final int TAG_LENGTH = 128;  /* Authentication tag length */

	private KeyGenerator keyGenerator;

	public AESEngine() throws NoSuchAlgorithmException {
		keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(256);
		logger.info("Initialized AES/GCM.");
	}

	public SecretKey generateKey() {
		return keyGenerator.generateKey();
	}

	public String encrypt(String plainText, SecretKey secretKey) throws Exception {
		/* Generate IV */
		byte[] iv = new byte[IV_LENGTH];
		new SecureRandom().nextBytes(iv);

		Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
		GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

		byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

		/* Combine IV + ciphertext */
		byte[] combined = new byte[iv.length + cipherText.length];
		System.arraycopy(iv, 0, combined, 0, iv.length);
		System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

		logger.info("Successfully encrypted text!");
		return Base64.getEncoder().encodeToString(combined);
	}

	public String decrypt(String encryptedText, SecretKey secretKey) throws Exception {
		byte[] combined = Base64.getDecoder().decode(encryptedText);

		/* Extract IV and ciphertext */
		byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
		byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

		Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
		GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
		cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

		byte[] plainText = cipher.doFinal(cipherText);

		logger.info("Successfully decrypted text!");
		return new String(plainText, StandardCharsets.UTF_8);
	}
}


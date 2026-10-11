package com.enixcoda.smsforward;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/** Sends forwards by email through the user's own SMTP account (e.g. Gmail with an app password). */
final class Mailer {
    private static final String TAG = "Mailer";
    private static final String TIMEOUT_MS = "15000";

    private Mailer() {
    }

    /** Blocking: call off the main thread. Returns a Forwarder result. */
    static int send(Context context, String to, String subject, String body) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String host = prefs.getString(context.getString(R.string.key_smtp_host), "").trim();
        String port = prefs.getString(context.getString(R.string.key_smtp_port), "").trim();
        String user = prefs.getString(context.getString(R.string.key_smtp_user), "").trim();
        String password = prefs.getString(context.getString(R.string.key_smtp_password), "");
        if (host.isEmpty() || port.isEmpty() || user.isEmpty() || password.isEmpty()) return Forwarder.FAIL;

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        // 587 = STARTTLS; anything else (465) = implicit TLS. Never plain text.
        props.put("587".equals(port) ? "mail.smtp.starttls.required" : "mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(user));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message);
            return Forwarder.OK;
        } catch (AuthenticationFailedException | AddressException | SendFailedException e) {
            Log.e(TAG, "Email rejected", e);
            return Forwarder.FAIL; // wrong password or address: retrying won't help
        } catch (MessagingException e) {
            Log.w(TAG, "Email failed, will retry: " + e);
            return Forwarder.RETRY; // connection or server trouble
        }
    }
}

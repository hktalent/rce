/*
 * Copyright (C) 2006-2015 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.core.component.sshremoteaccess.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.easymock.EasyMock.notNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.IOUtils;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPassword;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import de.rcenvironment.core.communication.api.PlatformService;
import de.rcenvironment.core.communication.common.NodeIdentifier;
import de.rcenvironment.core.communication.common.NodeIdentifierFactory;
import de.rcenvironment.core.communication.sshconnection.SshConnectionService;
import de.rcenvironment.core.component.model.api.ComponentInstallation;
import de.rcenvironment.core.component.registration.api.ComponentRegistry;
import de.rcenvironment.core.component.sshremoteaccess.SshRemoteAccessConstants;
import de.rcenvironment.core.utils.ssh.jsch.JschSessionFactory;
import de.rcenvironment.core.utils.ssh.jsch.SshParameterException;

/**
 * Tests for SSH Remote Access Service.
 *
 * @author Brigitte Boden
 */
public class SshRemoteAccessServiceImplTest {

    private SshServer sshServer;

    private SshConnectionService connectionService;

    private SshRemoteAccessClientServiceImpl remoteAccessService;

    private final NodeIdentifier dummyNodeId = NodeIdentifierFactory.fromHostAndNumberString("localhost:1");

    private ComponentRegistry mockRegistry;

    /**
     * Set up a dummy ssh server to connect to and setup mock connection service.
     * 
     * @throws IOException on unexpected error
     * @throws SshParameterException on SSH error
     * @throws JSchException on SSH error
     **/
    @SuppressWarnings("serial")
    @Before
    public void setup() throws IOException, JSchException, SshParameterException {
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(SshRemoteAccessClientTestConstants.PORT);
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshServer.setUserAuthFactories(new ArrayList<NamedFactory<UserAuth>>() {

            {
                add(new UserAuthPassword.Factory());
            }
        });
        sshServer.setPasswordAuthenticator(new PasswordAuthenticator() {

            @Override
            public boolean authenticate(String username, String password, ServerSession session) {
                return (username.equals(SshRemoteAccessClientTestConstants.USER) && password
                    .equals(SshRemoteAccessClientTestConstants.PASSWORD));
            }
        });
        // Command factory that returns the correct version for the command "ra protocol-version"
        sshServer.setCommandFactory(new CommandFactory() {

            @Override
            public Command createCommand(String commandString) {
                if (commandString.equals("ra list-tools")) {
                    return new Command() {

                        /** Test constant. */
                        public static final String EMPTY_STRING = "";

                        protected ExitCallback exitCallback;

                        private final CSVFormat csvFormat = CSVFormat.newFormat(' ').withQuote('"').withQuoteMode(QuoteMode.ALL);

                        private String stdout = csvFormat.format(SshRemoteAccessClientTestConstants.TOOL_NAME,
                            SshRemoteAccessClientTestConstants.TOOL_VERSION, SshRemoteAccessClientTestConstants.TOOL_HOST_ID,
                            SshRemoteAccessClientTestConstants.TOOL_HOST_NAME);

                        private String stderr;

                        private int exitValue;

                        private OutputStream stdoutStream;

                        private OutputStream stderrStream;

                        @Override
                        public void setInputStream(InputStream in) {}

                        @Override
                        public void setOutputStream(OutputStream out) {
                            this.stdoutStream = out;
                        }

                        @Override
                        public void setErrorStream(OutputStream err) {
                            this.stderrStream = err;
                        }

                        @Override
                        public void setExitCallback(ExitCallback callback) {
                            this.exitCallback = callback;
                        }

                        @Override
                        public void start(Environment env) throws IOException {

                            if (stdout != null) {
                                stdoutStream.write(stdout.getBytes());
                            } else {
                                stdoutStream.write(EMPTY_STRING.getBytes());
                            }
                            if (stderr != null) {
                                stderrStream.write(stderr.getBytes());
                            } else {
                                stderrStream.write(EMPTY_STRING.getBytes());
                            }
                            stdoutStream.flush();
                            stderrStream.flush();
                            IOUtils.closeQuietly(stdoutStream);
                            IOUtils.closeQuietly(stderrStream);
                            exitCallback.onExit(exitValue);
                        }

                        @Override
                        public void destroy() {}

                    };
                } else {
                    throw new IllegalArgumentException("Unknown command: " + commandString);
                }
            }
        });
        sshServer.start();

        Session session =
            JschSessionFactory.setupSession(SshRemoteAccessClientTestConstants.LOCALHOST, SshRemoteAccessClientTestConstants.PORT,
                SshRemoteAccessClientTestConstants.USER, null, SshRemoteAccessClientTestConstants.PASSWORD, null);
        connectionService = new MockSshConnectionService(session);
        remoteAccessService = new SshRemoteAccessClientServiceImpl();

        remoteAccessService.bindSSHConnectionService(connectionService);
        remoteAccessService.bindPlatformService(new PlatformService() {

            @Override
            public boolean isLocalNode(NodeIdentifier nodeId) {
                return false;
            }

            @Override
            public NodeIdentifier getLocalNodeId() {
                return dummyNodeId;
            }
        });

        mockRegistry = EasyMock.createMock(ComponentRegistry.class);
        remoteAccessService.bindComponentRegistry(mockRegistry);
    }

    /**
     * Test updating ssh remote access components.
     * 
     */
    @Test(timeout = SshRemoteAccessClientTestConstants.TIMEOUT)
    public void testUpdatingRemoteAccessComponents() {
        EasyMock.reset(mockRegistry);
        mockRegistry.addComponent(notNull(ComponentInstallation.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {

            @Override
            public Void answer() throws Throwable {
                ComponentInstallation ci = (ComponentInstallation) EasyMock.getCurrentArguments()[0];
                assertNotNull(ci);
                assertEquals(SshRemoteAccessClientTestConstants.TOOL_VERSION, ci.getComponentRevision().getComponentInterface()
                    .getVersion());
                assertEquals(SshRemoteAccessConstants.GROUP_NAME, ci.getComponentRevision().getComponentInterface()
                    .getGroupName());
                return null;
            }
        });
        EasyMock.replay(mockRegistry);
        remoteAccessService.updateSshRemoteAccessComponents();
    }

    /**
     * Stop ssh server.
     * 
     * @throws InterruptedException on error when stopping the server
     * @throws IOException on unexpected error
     **/
    @After
    public void tearDown() throws InterruptedException, IOException {
        sshServer.stop();
    }

}
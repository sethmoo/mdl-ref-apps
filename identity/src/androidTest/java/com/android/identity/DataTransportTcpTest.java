/*
 * Copyright 2022n The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity;

import android.content.Context;
import android.os.ConditionVariable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class DataTransportTcpTest {

    @Test
    @SmallTest
    public void setupReceivedWhileWaitingForConnection() {
        Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
        DataTransportTcp prover = new DataTransportTcp(appContext, Constants.LOGGING_FLAG_MAXIMUM);

        ConditionVariable proverSetupCompletedCondVar = new ConditionVariable();

        Executor executor = Executors.newSingleThreadExecutor();

        prover.setListener(new DataTransport.Listener() {
            @Override
            public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                proverSetupCompletedCondVar.open();
            }

            @Override
            public void onListeningPeerConnecting() {
                Assert.fail();
            }

            @Override
            public void onListeningPeerConnected() {
                Assert.fail();
            }

            @Override
            public void onListeningPeerDisconnected() {
                Assert.fail();
            }

            @Override
            public void onConnectionResult(@Nullable Throwable error) {
                Assert.fail();
            }

            @Override
            public void onConnectionDisconnected() {
                Assert.fail();
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
                Assert.fail();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Assert.fail();
            }

        }, executor);

        prover.listen();
        Assert.assertTrue(proverSetupCompletedCondVar.block(5000));
        prover.close();
    }

    @Test
    @SmallTest
    public void connectAndListen() {

        Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
        DataTransportTcp verifier = new DataTransportTcp(appContext,
                Constants.LOGGING_FLAG_MAXIMUM);
        DataTransportTcp prover = new DataTransportTcp(appContext, Constants.LOGGING_FLAG_MAXIMUM);

        byte[] messageSentByVerifier = Util.fromHex("010203");
        byte[] messageSentByProver = Util.fromHex("0405");

        final byte[][] messageReceivedByProver = {null};
        final byte[][] messageReceivedByVerifier = {null};

        final ConditionVariable proverListeningSetupCompleteCondVar = new ConditionVariable();
        final ConditionVariable proverMessageReceivedCondVar = new ConditionVariable();
        final ConditionVariable proverPeerConnectingCondVar = new ConditionVariable();
        final ConditionVariable proverPeerConnectedCondVar = new ConditionVariable();
        final ConditionVariable proverPeerDisconnectedCondVar = new ConditionVariable();
        final ConditionVariable verifierMessageReceivedCondVar = new ConditionVariable();
        final ConditionVariable verifierPeerConnectedCondVar = new ConditionVariable();
        Executor executor = Executors.newSingleThreadExecutor();

        prover.setListener(new DataTransport.Listener() {
            @Override
            public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                proverListeningSetupCompleteCondVar.open();
            }

            @Override
            public void onListeningPeerConnecting() {
                proverPeerConnectingCondVar.open();
            }

            @Override
            public void onListeningPeerConnected() {
                proverPeerConnectedCondVar.open();
            }

            @Override
            public void onListeningPeerDisconnected() {
                proverPeerDisconnectedCondVar.open();
            }

            @Override
            public void onConnectionResult(@Nullable Throwable error) {
                Assert.fail();
            }

            @Override
            public void onConnectionDisconnected() {
                Assert.fail();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
                messageReceivedByProver[0] = data.clone();
                proverMessageReceivedCondVar.open();
            }
        }, executor);

        verifier.setListener(new DataTransport.Listener() {
            @Override
            public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                Assert.fail();
            }

            @Override
            public void onListeningPeerConnecting() {
                Assert.fail();
            }

            @Override
            public void onListeningPeerConnected() {
                Assert.fail();
            }

            @Override
            public void onListeningPeerDisconnected() {
                Assert.fail();
            }

            @Override
            public void onConnectionResult(@Nullable Throwable error) {
                Assert.assertNull(error);
                verifierPeerConnectedCondVar.open();
            }

            @Override
            public void onConnectionDisconnected() {
                Assert.fail();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Assert.fail();
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
                messageReceivedByVerifier[0] = data.clone();
                verifierMessageReceivedCondVar.open();
            }
        }, executor);

        prover.listen();
        Assert.assertTrue(proverListeningSetupCompleteCondVar.block(5000));
        DataRetrievalAddress listeningAddress = prover.getListeningAddress();
        verifier.connect(listeningAddress);

        Assert.assertTrue(proverPeerConnectingCondVar.block(5000));
        Assert.assertTrue(verifierPeerConnectedCondVar.block(5000));
        verifier.sendMessage(messageSentByVerifier);

        Assert.assertTrue(proverPeerConnectedCondVar.block(5000));
        prover.sendMessage(messageSentByProver);

        // Since we send the message over a socket and this data is received in a separate thread
        // we need to sit here and wait until this data is delivered to the callbacks above.
        //

        Assert.assertTrue(proverMessageReceivedCondVar.block(5000));
        Assert.assertTrue(verifierMessageReceivedCondVar.block(5000));

        Assert.assertArrayEquals(messageSentByVerifier, messageReceivedByProver[0]);
        Assert.assertArrayEquals(messageSentByProver, messageReceivedByVerifier[0]);

        verifier.close();
        Assert.assertTrue(proverPeerDisconnectedCondVar.block(5000));

        // TODO: also test the path where the prover calls disconnect().
    }
}

package com.telerelay.di

import android.content.Context
import android.os.Build
import com.telerelay.core.AndroidClock
import com.telerelay.data.crypto.KeystoreAesGcmCipher
import com.telerelay.data.crypto.SecretCipher
import com.telerelay.data.privacy.RegexPrivacyFilter
import com.telerelay.data.service.AndroidServiceController
import com.telerelay.data.settings.SettingsRepositoryImpl
import com.telerelay.data.sim.SubscriptionSimInfoProvider
import com.telerelay.data.telephony.CallLogNumberResolver
import com.telerelay.data.telephony.LegacyPhoneStateMonitor
import com.telerelay.data.telephony.TelephonyCallMonitor
import com.telerelay.data.telegram.TelegramGatewayImpl
import com.telerelay.data.work.WorkManagerMessageSender
import com.telerelay.domain.logic.MessageFormatterImpl
import com.telerelay.domain.logic.MultipartSmsAssembler
import com.telerelay.domain.port.CallMonitor
import com.telerelay.domain.port.CallerNumberResolver
import com.telerelay.domain.port.Clock
import com.telerelay.domain.port.MessageFormatter
import com.telerelay.domain.port.MessageSender
import com.telerelay.domain.port.PrivacyFilter
import com.telerelay.domain.port.ServiceController
import com.telerelay.domain.port.SettingsRepository
import com.telerelay.domain.port.SimInfoProvider
import com.telerelay.domain.port.TelegramGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Maps domain ports to their data-layer implementations. Swapping an
 * implementation (e.g. retry-capable sender, regex filter) only changes a line here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun clock(impl: AndroidClock): Clock

    @Binds
    @Singleton
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun telegramGateway(impl: TelegramGatewayImpl): TelegramGateway

    @Binds
    @Singleton
    abstract fun messageSender(impl: WorkManagerMessageSender): MessageSender

    @Binds
    @Singleton
    abstract fun privacyFilter(impl: RegexPrivacyFilter): PrivacyFilter

    @Binds
    @Singleton
    abstract fun secretCipher(impl: KeystoreAesGcmCipher): SecretCipher

    @Binds
    @Singleton
    abstract fun simInfoProvider(impl: SubscriptionSimInfoProvider): SimInfoProvider

    @Binds
    @Singleton
    abstract fun callerNumberResolver(impl: CallLogNumberResolver): CallerNumberResolver

    @Binds
    @Singleton
    abstract fun serviceController(impl: AndroidServiceController): ServiceController

    @Binds
    abstract fun messageFormatter(impl: MessageFormatterImpl): MessageFormatter

    companion object {

        @Provides
        @Singleton
        fun zoneId(): ZoneId = ZoneId.systemDefault()

        @Provides
        @Singleton
        fun multipartSmsAssembler(clock: Clock): MultipartSmsAssembler =
            MultipartSmsAssembler(clock, quietWindowMillis = MultipartSmsAssembler.DEFAULT_QUIET_WINDOW_MILLIS)

        /** API 31+ uses TelephonyCallback; earlier devices fall back to PhoneStateListener. */
        @Provides
        @Singleton
        fun callMonitor(
            @ApplicationContext context: Context,
            clock: Clock,
        ): CallMonitor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TelephonyCallMonitor(context, clock)
        } else {
            LegacyPhoneStateMonitor(context, clock)
        }
    }
}

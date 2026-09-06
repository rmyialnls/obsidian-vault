package com.tracksnatcher.app.di

import com.tracksnatcher.app.billing.PlayBillingSubscriptionManager
import com.tracksnatcher.app.billing.SubscriptionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionManager(impl: PlayBillingSubscriptionManager): SubscriptionManager
}

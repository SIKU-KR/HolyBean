package eloom.holybean.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PrinterDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class UsbTransport
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PiTransport

package uniffi.atman

// UniFFI 0.28's Kotlin generator skips async constructors ("Note no
// constructor generated for this object as it is async."), so we hand-
// roll the call against the FFI symbols it does generate. Lives in
// `uniffi.atman` to access the `internal` `uniffiRustCallAsync` /
// `UniffiLib` helpers. Drop once we move to UniFFI ≥ 0.29.

suspend fun createAtmanClient(
    identityHex: String,
    networkKeyHex: String,
    customRelayUrl: String?,
    syncmanDir: String,
    syncIntervalSecs: ULong,
): AtmanClient = uniffiRustCallAsync(
    UniffiLib.INSTANCE.uniffi_atman_fn_constructor_atmanclient_new(
        FfiConverterString.lower(identityHex),
        FfiConverterString.lower(networkKeyHex),
        FfiConverterOptionalString.lower(customRelayUrl),
        FfiConverterString.lower(syncmanDir),
        syncIntervalSecs.toLong(),
    ),
    { future, callback, continuation ->
        UniffiLib.INSTANCE.ffi_atman_rust_future_poll_pointer(future, callback, continuation)
    },
    { future, continuation ->
        UniffiLib.INSTANCE.ffi_atman_rust_future_complete_pointer(future, continuation)
    },
    { future -> UniffiLib.INSTANCE.ffi_atman_rust_future_free_pointer(future) },
    { ptr -> FfiConverterTypeAtmanClient.lift(ptr) },
    AtmanException.ErrorHandler,
)

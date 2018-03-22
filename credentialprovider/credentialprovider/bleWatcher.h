#pragma once
#include <windows.h>
#include "proxlockProvider.h"
#include <iostream>
#include <Windows.Foundation.h>
#include <wrl\wrappers\corewrappers.h>
#include <wrl\client.h>
#include <wrl\event.h>
#include <stdio.h>
#include <windows.devices.bluetooth.h>
#include <windows.devices.bluetooth.advertisement.h>
#include <memory>
#include <functional>
#include <tchar.h>
#include <windows.foundation.collections.h>
#include <windows.storage.streams.h>
#include <robuffer.h>
#include <windows.security.cryptography.h>
#include <conio.h>
#include <fstream>
#include <string>
#include <sstream>

using namespace std;
using namespace Microsoft::WRL;
using namespace Microsoft::WRL::Wrappers;
using namespace ABI::Windows::Foundation;
using namespace ABI::Windows::Devices::Bluetooth::Advertisement;
using namespace ABI::Windows::UI::Input;
using namespace ABI::Windows::Storage::Streams;
using namespace Windows::Storage::Streams;


class BLEWatcher
{
public:
	BLEWatcher(void);
	~BLEWatcher(void);
	HRESULT Initialize(proxlockProvider* pProvider);
	BOOL GetConnectedStatus();

private:

	HRESULT OnAdvertisementReceived(IBluetoothLEAdvertisementWatcher* watcher, IBluetoothLEAdvertisementReceivedEventArgs* args);
	proxlockProvider																								*_pProvider;        // Pointer to our owner.
	EventRegistrationToken																						*watcherToken;
	ComPtr<IBluetoothLEAdvertisementWatcher>																	watcher;
	ComPtr<IBluetoothLEAdvertisementFilter>																		filter;
	ComPtr<IBluetoothLEAdvertisementWatcherFactory>																advWatcherFactory;
	ComPtr<ITypedEventHandler<BluetoothLEAdvertisementWatcher*, BluetoothLEAdvertisementReceivedEventArgs*>>	handler;
	boolean																										connected;
	boolean																										sentSuccess;
	string																										aesKey;
	int																											rssi;
};
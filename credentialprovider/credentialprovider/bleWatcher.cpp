#include "bleWatcher.h"
#include "AES.h"
#include <ctime>




int PrintError(unsigned int line, HRESULT hr)
{
	wprintf_s(L"ERROR: Line:%d HRESULT: 0x%X\n", line, hr);
	return hr;
}

int IsBigEndian()
{
	unsigned int i = 2;
	char *c = (char*)&i;
	if (*c)
	{
		//cout<<"little endian";
		return 0;
	}
	else
	{
		//cout<<"big endian";
		return 1;
	}
}

long ConvertByteToLong(char *take, int startIndex)
{
	//if machines is liitle endian convert and return as this
	if (!IsBigEndian())
	{

		return (((take[startIndex + 0] & 0xff) << 24) | ((take[startIndex + 1] & 0xff) << 16)
			| ((take[startIndex + 2] & 0xff) << 8) | ((take[startIndex + 3] & 0xff)));
	}
	else
	{

		return (((take[startIndex + 3] & 0xff) << 24) | ((take[startIndex + 2] & 0xff) << 16)
			| ((take[startIndex + 1] & 0xff) << 8) | ((take[startIndex + 0] & 0xff)));
	}
	//if machine is big endian convert and return as long
}



BLEWatcher::BLEWatcher(void)
{
	_pProvider = NULL;
	watcherToken = nullptr;
	watcher = nullptr;
	filter = nullptr;
	advWatcherFactory = nullptr;
	connected = FALSE;
	sentSuccess = FALSE;
	ifstream inFile;
	string line;
	inFile.open("E:\proxlock.conf");
	while (getline(inFile, line)) {
		istringstream is_line(line);
		string key;
		if (getline(is_line, key, '=')) {
			string value;
			if (getline(is_line, value)) {
				if (key == "Key")
					aesKey = value;
				if (key == "RSSI")
					rssi = stoi(value);
			}
		}

	}
	inFile.close();
}

BLEWatcher::~BLEWatcher(void)
{

}

HRESULT BLEWatcher::OnAdvertisementReceived(IBluetoothLEAdvertisementWatcher* watcher, IBluetoothLEAdvertisementReceivedEventArgs* args)
{
	INT16 receivedRSSI;
	args->get_RawSignalStrengthInDBm(&receivedRSSI);
	if (receivedRSSI > rssi) {
		long secondsFromEpoch = static_cast<long> (time(NULL));
		try {

			ComPtr<IBluetoothLEAdvertisement> advertisement;
			args->get_Advertisement(&advertisement);

			ComPtr<__FIVector_1_Windows__CDevices__CBluetooth__CAdvertisement__CBluetoothLEAdvertisementDataSection> dataSections;
			advertisement->get_DataSections(&dataSections);
			ComPtr<IBluetoothLEAdvertisementDataSection> dataSection;
			dataSections->GetAt(2, &dataSection);
			ComPtr<IBuffer> buffer;
			dataSection->get_Data(&buffer);
			ComPtr<IBufferByteAccess> bufferByteAccess;
			buffer.As(&bufferByteAccess);
			byte* bytes = nullptr;
			bufferByteAccess->Buffer(&bytes);
			AES decryptor;
			decryptor.SetParameters(128);
			unsigned char* btKey = new unsigned char[aesKey.length() / 2]();
			for (int i = 0; i < aesKey.length(); i += 2) {
				string hexString = aesKey.substr(i, 2);
				btKey[i / 2] = (unsigned char)strtol(hexString.c_str(), NULL, 16);

			}
			unsigned char dataToDecrypt[16];
			//i+2 because the first 2 bytes are garbage, i don't know, android or ble weirdness
			for (int i = 0; i < 16; i++)
				dataToDecrypt[i] = bytes[i + 2];
			//MessageBoxA(0, (char*) dataToDecrypt, ("messageBox caption"), MB_OK);

			decryptor.StartDecryption(btKey);

			//unsigned char* dataToDecrypt = reinterpret_cast<unsigned char*>(bytes);
			unsigned char* decryptedData = new unsigned char[16]();
			decryptor.DecryptBlock(dataToDecrypt, decryptedData);

			//char hexstr[201];
			//int i;
			//for (i = 0; i < 16; i++) {
			//	sprintf(hexstr + i * 2, "%02x", (byte)decryptedData[i]);
			//}
			//hexstr[i * 2] = 0;
			long decryptedTimeStamp = ConvertByteToLong(reinterpret_cast<char*>(decryptedData), 4);



			//TCHAR str[255];
			//_stprintf(str, _T("seconds is %d"), secondsFromEpoch);
			//MessageBox(0, str, 0, MB_OK);
			//MessageBoxA(0, hexstr, 0, MB_OK);



			/*if (bytes == nullptr)
			MessageBoxA(0, "Null Bytes ptr", ("MessageBox caption"), MB_OK);*/
			//else
				//MessageBoxA(0, reinterpret_cast<char*>(decryptedData), ("messageBox caption"), MB_OK);
				//MessageBoxA(0, string, ("MessageBox caption"), MB_OK);

			/*
			IBluetoothLEAdvertisement** advertisement;
			advertisement = new IBluetoothLEAdvertisement*;
			args->get_Advertisement(advertisement);



			__FIVectorView_1_Windows__CDevices__CBluetooth__CAdvertisement__CBluetoothLEAdvertisementDataSection** dataSections;
			dataSections = new __FIVectorView_1_Windows__CDevices__CBluetooth__CAdvertisement__CBluetoothLEAdvertisementDataSection*;
			(*advertisement)->get_DataSections(dataSections);
			IBluetoothLEAdvertisementDataSection** dataSection;
			(*dataSections)->GetAt(2, dataSection);
			ABI::Windows::Storage::Streams::IBuffer** buffer;
			(*dataSection)->get_Data(buffer);
			ComPtr<ABI::Windows::Storage::Streams::IDataReader> reader;
			UINT32 *length;
			(*buffer)->get_Length(length);
			ComPtr<IInspectable> insp(reinterpret_cast<IInspectable*>(buffer));
			ComPtr<Windows::Storage::Streams::IBufferByteAccess> bufferByteAccess;
			insp.As(&bufferByteAccess);

			byte* dataByteArr = nullptr;
			bufferByteAccess->Buffer(&dataByteArr);
			if(dataByteArr[1])
				MessageBox(0, _T("And text here"), _T("MessageBox caption"), MB_OK);

				*/
			if (!sentSuccess) {
				long timeDif = (secondsFromEpoch - decryptedTimeStamp);
				if (timeDif >= 0 && timeDif < 60) {
					sentSuccess = TRUE;
					connected = TRUE;

					_pProvider->OnConnectStatusChanged();
				}
			}
		}
		catch (exception e) {
			//MessageBox(0, _T("caught"), _T("MessageBox caption"), MB_OK);
		}


	}
		//IVector<BluetoothLEAdvertisementDataSection> dataSections = *advertisement->get_DataSections;

	return S_OK;
}

BOOL BLEWatcher::GetConnectedStatus()
{
	return connected;
}

HRESULT BLEWatcher::Initialize(proxlockProvider *pProvider)
{
	watcherToken = new EventRegistrationToken();

	// Initialize the Windows Runtime. Will cause crash not needed.
	/*RoInitializeWrapper initialize(RO_INIT_MULTITHREADED);
	if (FAILED(initialize))
	{
		return PrintError(__LINE__, initialize);
	}*/

	HRESULT hr = S_OK;

	// Be sure to add a release any existing provider we might have, then add a reference
	// to the provider we're working with now.
	if (_pProvider != NULL)
	{
		_pProvider->Release();
	}
	_pProvider = pProvider;
	_pProvider->AddRef();

	
	//create the watcher factory
	hr = GetActivationFactory(HStringReference(RuntimeClass_Windows_Devices_Bluetooth_Advertisement_BluetoothLEAdvertisementWatcher).Get(), &advWatcherFactory);
	if (FAILED(hr))
	{
		return PrintError(__LINE__, hr);
	}
	
	//create the advertisement filter
	Wrappers::HStringReference class_id_filter2(RuntimeClass_Windows_Devices_Bluetooth_Advertisement_BluetoothLEAdvertisementFilter);
	hr = RoActivateInstance(class_id_filter2.Get(), reinterpret_cast<IInspectable**>(filter.GetAddressOf()));

	//create the watcher
	hr = advWatcherFactory->Create(filter.Get(), &watcher);
	if (watcher == NULL)
	{
		cout << "watcher is NULL, err is " << hex << hr;
	}
	else
	{
		handler = Callback<ITypedEventHandler<BluetoothLEAdvertisementWatcher*, BluetoothLEAdvertisementReceivedEventArgs*> >
			(std::bind(
				&BLEWatcher::OnAdvertisementReceived,
				this,
				placeholders::_1,
				placeholders::_2
			));
	}

	//add event handler to watcher
	hr = watcher->add_Received(handler.Get(), watcherToken);

	//start watcher
	watcher->Start();
	
	return hr;
}


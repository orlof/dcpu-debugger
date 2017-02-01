package org.megastage.emulator;

import java.io.*;

public class FloppyDisk {
	public char[] data = new char[737280];

	private boolean writeProtected;
	private VirtualFloppyDrive drive;

    public FloppyDisk(InputStream is) {
        try {
            load(is);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
    
	public void load(InputStream is) throws IOException {
		DataInputStream dis = new DataInputStream(new BufferedInputStream(is));
		int i = 0;
		try {
			for (; i<data.length; i++) {
				data[i] = dis.readChar();
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		} catch (IOException e) {
			for (; i<data.length; i++) {
				data[i] = 0;
			}
        } finally {
			dis.close();
		}	
	}

	public void save(File file) throws IOException {
		DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
		try {
			for (int i = 0; i < data.length; i++) {
				dos.writeChar(data[i]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		dos.close();
	}
	
	public boolean isWriteProtected() {
		return writeProtected;
	}

	public void setWriteProtected(boolean writeProtected) {
		this.writeProtected = writeProtected;
	}

	public void inserted(VirtualFloppyDrive drive)
	{
		this.drive = drive;
	}
	
	public void ejected() {
		this.drive = null;
	}

	public VirtualFloppyDrive getDriveUsing() {
		return drive;
	}
	
}
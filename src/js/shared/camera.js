const startCameraButton = document.getElementById('start-camera');
const takePhotoButton = document.getElementById('take-photo');
const retakePhotoButton = document.getElementById('retake-photo');
const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const photoPreview = document.getElementById('photo-preview');
const fotoInput = document.getElementById('foto');

if (startCameraButton && takePhotoButton && retakePhotoButton && video && canvas && photoPreview && fotoInput) {
    let stream;

    async function openCamera() {
        try {
            stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
            video.srcObject = stream;
            video.style.display = 'block';
            takePhotoButton.style.display = 'inline-block';
            startCameraButton.style.display = 'none';
            photoPreview.style.display = 'none';
            retakePhotoButton.style.display = 'none';
        } catch (error) {
            alert('No se pudo abrir la cámara. Comprueba los permisos o usa un dispositivo compatible.');
            console.error('Error al abrir la cámara:', error);
        }
    }

    function stopCamera() {
        if (stream) {
            stream.getTracks().forEach(track => track.stop());
            stream = null;
        }
        video.srcObject = null;
        video.style.display = 'none';
        takePhotoButton.style.display = 'none';
    }

    function capturePhoto() {
        const width = video.videoWidth;
        const height = video.videoHeight;
        if (!width || !height) {
            alert('La cámara aún no está lista. Intenta de nuevo.');
            return;
        }
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext('2d');
        context.drawImage(video, 0, 0, width, height);
        const imageData = canvas.toDataURL('image/png');
        fotoInput.value = imageData;
        photoPreview.src = imageData;
        photoPreview.style.display = 'block';
        takePhotoButton.style.display = 'none';
        retakePhotoButton.style.display = 'inline-block';
        stopCamera();
    }

    startCameraButton.addEventListener('click', openCamera);
    takePhotoButton.addEventListener('click', capturePhoto);
    retakePhotoButton.addEventListener('click', () => {
        photoPreview.style.display = 'none';
        fotoInput.value = '';
        openCamera();
    });

    const form = fotoInput.closest('form');
    if (form) {
        form.addEventListener('submit', event => {
            if (!fotoInput.value) {
                event.preventDefault();
                alert('Debes tomar una foto antes de enviar.');
            }
        });
    }
}